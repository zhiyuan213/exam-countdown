# -*- coding: utf-8 -*-
"""
生成「转发给朋友」分享卡片封面图（5:4，750x600）。
对标美团小程序转发卡：海报化「假 UI」风格——品牌行 + 大标语 + 倒计时卡片 + 考试标签宫格。
输出：miniprogram/images/share-cover.jpg
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 750, 600
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-cover.jpg')

FONT_BD = 'C:/Windows/Fonts/msyhbd.ttc'
FONT_RG = 'C:/Windows/Fonts/msyh.ttc'

NAVY = (34, 52, 90)
BLUE = (44, 90, 160)
GRAY = (90, 107, 133)
RED = (214, 69, 61)


def font(path, size):
    return ImageFont.truetype(path, size)


def vgrad(top, bottom):
    img = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)
    return img


def calendar_icon(img, cx, cy, w=110):
    """红色表头日历图标（贴在卡片/品牌行用）"""
    h = w
    x0, y0 = cx - w // 2, cy - h // 2
    x1, y1 = cx + w // 2, cy + h // 2
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x0, y0, x1, y1], radius=16, fill=(255, 255, 255), outline=(226, 233, 243), width=2)
    d.rounded_rectangle([x0, y0, x1, y0 + 34], radius=16, fill=RED)
    d.rectangle([x0, y0 + 22, x1, y0 + 34], fill=RED)
    for bx in (cx - 26, cx + 26):
        d.rounded_rectangle([bx - 6, y0 - 10, bx + 6, y0 + 12], radius=6, fill=WHITE)
    # 大数字 7
    f = font(FONT_BD, int(w * 0.42))
    d.text((cx, y0 + 34 + (h - 34) / 2), '7', font=f, fill=RED, anchor='mm')
    return img


WHITE = (255, 255, 255)


def main():
    img = vgrad((246, 250, 254), (223, 236, 251))

    # ---- 倒计时卡片投影 ----
    shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ds = ImageDraw.Draw(shadow)
    ds.rounded_rectangle([96, 232, 666, 412], radius=26, fill=(60, 90, 140, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(10))
    img.paste(shadow, (0, 6), shadow)

    d = ImageDraw.Draw(img)

    # ---- 品牌行（左上：日历 + 名称）----
    calendar_icon(img, 78, 74, 84)
    d = ImageDraw.Draw(img)
    d.text((138, 74), '考试倒计时', font=font(FONT_BD, 46), fill=NAVY, anchor='lm')
    # 右上角小程序 pill
    d.rounded_rectangle([566, 48, 702, 100], radius=26, fill=(232, 242, 253))
    d.text((634, 74), '小程序', font=font(FONT_RG, 28), fill=BLUE, anchor='mm')

    # ---- 大标语 ----
    d.text((W / 2, 158), '重要考试 尽在掌握', font=font(FONT_BD, 58), fill=NAVY, anchor='mm')

    # ---- 倒计时卡片（假 UI 核心）----
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([90, 226, 660, 406], radius=26, fill=WHITE, outline=(228, 236, 246), width=2)
    d.text((136, 272), '距 2027 考研初试', font=font(FONT_RG, 32), fill=GRAY, anchor='lm')
    d.text((136, 344), '128', font=font(FONT_BD, 96), fill=RED, anchor='lm')
    w128 = d.textlength('128', font=font(FONT_BD, 96))
    d.text((152 + w128, 356), '天', font=font(FONT_BD, 38), fill=NAVY, anchor='lm')
    # 卡片右侧小日历
    calendar_icon(img, 560, 316, 120)

    # ---- 考试标签宫格（一排彩色 pill）----
    chips = [
        ('考研', (232, 242, 253), BLUE),
        ('考公', (255, 243, 224), (196, 128, 30)),
        ('教资', (232, 248, 238), (32, 142, 96)),
        ('四六级', (255, 235, 235), (198, 68, 68)),
        ('高考', (238, 234, 251), (108, 84, 190)),
    ]
    f_chip = font(FONT_BD, 30)
    pad_x, gap = 30, 18
    widths = [d.textlength(t, font=f_chip) + pad_x * 2 for t, _, _ in chips]
    total = sum(widths) + gap * (len(chips) - 1)
    x = (W - total) / 2
    for (t, bgc, fgc), w in zip(chips, widths):
        d.rounded_rectangle([x, 452, x + w, 512], radius=30, fill=bgc)
        d.text((x + w / 2, 481), t, font=f_chip, fill=fgc, anchor='mm')
        x += w + gap

    # ---- 底部注脚 ----
    d.text((W / 2, 556), '微信搜索「考试倒计时」· 订阅提醒不错过', font=font(FONT_RG, 28), fill=GRAY, anchor='mm')

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, 'JPEG', quality=88, optimize=True, progressive=True)
    print('saved', os.path.abspath(OUT), os.path.getsize(OUT), 'bytes', img.size)


if __name__ == '__main__':
    main()
