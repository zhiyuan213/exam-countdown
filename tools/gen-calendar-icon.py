# -*- coding: utf-8 -*-
"""生成首页/下拉刷新用的日历图标 calendar-red.png。

风格（用户 2026-10-11 定稿）：整块红底 + 大号白色日期数字，无月份条、无白底。
改日期或配色：改下面 DATE / RED 两个常量后重跑即可，勿直接手改 PNG。
"""
from PIL import Image, ImageDraw, ImageFont

DATE = "17"            # 显示的日期数字
RED_TOP = (255, 77, 66)    # 顶部略亮，做轻微纵向渐变
RED_BOTTOM = (250, 50, 40) # 主体红（接近 iOS #FF3B30）
SIZE = 768             # 渲染尺寸，最后缩到 384 输出（抗锯齿）
OUT = r"D:/code/exam-countdown/miniprogram/images/calendar-red.png"

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 圆角方块（iOS 图标约 22.4% 圆角），红底纵向微渐变
radius = int(SIZE * 0.224)
body = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
bd = ImageDraw.Draw(body)
for y in range(SIZE):
    t = y / SIZE
    c = tuple(int(RED_TOP[i] + (RED_BOTTOM[i] - RED_TOP[i]) * t) for i in range(3)) + (255,)
    bd.line([(0, y), (SIZE, y)], fill=c)
mask = Image.new("L", (SIZE, SIZE), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=radius, fill=255)
img.paste(body, (0, 0), mask)

# 白色大号日期，居中（略上移，视觉居中）
font = ImageFont.truetype(r"C:/Windows/Fonts/arialbd.ttf", int(SIZE * 0.52))
left, top, right, bottom = d.textbbox((0, 0), DATE, font=font)
tw, th = right - left, bottom - top
x = (SIZE - tw) / 2 - left
y = (SIZE - th) / 2 - top - SIZE * 0.02
d.text((x, y), DATE, font=font, fill=(255, 255, 255, 255))

img = img.resize((384, 384), Image.LANCZOS)
img.save(OUT)
print("saved", OUT)
