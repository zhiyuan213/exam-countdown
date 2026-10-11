# -*- coding: utf-8 -*-
"""生成首页/下拉刷新用的日历图标 calendar-red.png。

风格（用户 2026-10-11 定稿，参考 iOS 日历图标）：
红头白字月份 + 白底深灰大号日期 + 顶部两个装订环。
改月份/日期/配色：改下面 MONTH / DATE 常量后重跑即可，勿直接手改 PNG。
"""
from PIL import Image, ImageDraw, ImageFont

MONTH = "July"         # 头部月份文字
DATE = "17"            # 日期数字
RED = (255, 59, 48)    # 头部红（iOS #FF3B30）
DARK = (32, 32, 36)    # 日期深灰
FONT = r"C:/Windows/Fonts/segoeuib.ttf"  # Segoe UI Bold：美式无衬线（近 Helvetica/SF）
SIZE = 768             # 渲染尺寸，最后缩到 384 输出（抗锯齿）
OUT = r"D:/code/exam-countdown/miniprogram/images/calendar-red.png"

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

# 卡片圆角遮罩（iOS 约 22.4% 圆角）
radius = int(SIZE * 0.224)
card_mask = Image.new("L", (SIZE, SIZE), 0)
ImageDraw.Draw(card_mask).rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=radius, fill=255)

# 白底（极浅的纵向渐变增加质感）
body = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
bd = ImageDraw.Draw(body)
for y in range(SIZE):
    t = y / SIZE
    v = int(255 - 6 * t)
    bd.line([(0, y), (SIZE, y)], fill=(v, v, v + 2 if v + 2 <= 255 else 255, 255))
img.paste(body, (0, 0), card_mask)

# 红色头部（约占 36% 高，通过卡片遮罩裁掉上缘外扩部分）
header_h = int(SIZE * 0.36)
header = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
ImageDraw.Draw(header).rectangle([0, 0, SIZE - 1, header_h], fill=RED + (255,))
band_mask = Image.new("L", (SIZE, SIZE), 0)
ImageDraw.Draw(band_mask).rectangle([0, 0, SIZE - 1, header_h], fill=255)
band_mask = Image.composite(band_mask, Image.new("L", (SIZE, SIZE), 0), card_mask)
img.paste(header, (0, 0), band_mask)

d = ImageDraw.Draw(img)

# 顶部两个装订环（浅灰小胶囊，压在卡片上缘）
ring_w, ring_h = int(SIZE * 0.045), int(SIZE * 0.09)
for cx in (int(SIZE * 0.28), int(SIZE * 0.72)):
    x0, y0 = cx - ring_w // 2, -int(ring_h * 0.25)
    d.rounded_rectangle([x0, y0, x0 + ring_w, y0 + ring_h],
                        radius=ring_w // 2, fill=(210, 210, 215, 255))

# 头部月份：白色粗体，按宽度自适应
mf = ImageFont.truetype(FONT, int(header_h * 0.62))
while d.textbbox((0, 0), MONTH, font=mf)[2] > SIZE * 0.66:
    mf = ImageFont.truetype(FONT, mf.size - 8)
l, t, r, b = d.textbbox((0, 0), MONTH, font=mf)
d.text(((SIZE - (r - l)) / 2 - l, (header_h - (b - t)) / 2 - t), MONTH,
       font=mf, fill=(255, 255, 255, 255))

# 日期：深灰大号粗体，居中于白色区域（略上移做视觉居中）
df = ImageFont.truetype(FONT, int(SIZE * 0.42))
l, t, r, b = d.textbbox((0, 0), DATE, font=df)
y0 = header_h + (SIZE - header_h - (b - t)) / 2 - t - SIZE * 0.015
d.text(((SIZE - (r - l)) / 2 - l, y0), DATE, font=df, fill=DARK + (255,))

# 浅灰描边：白底在浅色背景上需要边界
bw = max(2, int(SIZE * 0.012))
ImageDraw.Draw(img).rounded_rectangle(
    [bw // 2, bw // 2, SIZE - 1 - bw // 2, SIZE - 1 - bw // 2],
    radius=radius - bw // 2, outline=(222, 222, 228, 255), width=bw)

img = img.resize((384, 384), Image.LANCZOS)
img.save(OUT)
print("saved", OUT)
