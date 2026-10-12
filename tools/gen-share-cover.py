# -*- coding: utf-8 -*-
"""
生成「转发给朋友」分享卡片封面图（5:4，750x600）。
输出：miniprogram/images/share-cover.jpg
"""
import os
from PIL import Image, ImageDraw, ImageFont

W, H = 750, 600
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-cover.jpg')

FONT_BD = 'C:/Windows/Fonts/msyhbd.ttc'
FONT_RG = 'C:/Windows/Fonts/msyh.ttc'


def font(path, size):
    return ImageFont.truetype(path, size)


def vgrad(top, bottom):
    """纵向渐变底"""
    img = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)
    return img


def calendar_badge(d, cx, cy, w=150, h=150):
    """白色日历图标 + 红色表头"""
    x0, y0, x1, y1 = cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2
    d.rounded_rectangle([x0, y0, x1, y1], radius=22, fill=(255, 255, 255))
    d.rounded_rectangle([x0, y0, x1, y0 + 46], radius=22, fill=(214, 69, 61))
    d.rectangle([x0, y0 + 30, x1, y0 + 46], fill=(214, 69, 61))
    # 装订环
    for bx in (cx - 38, cx + 38):
        d.rounded_rectangle([bx - 9, y0 - 14, bx + 9, y0 + 16], radius=9, fill=(255, 255, 255))
    # 日期点阵
    dx0, dy0 = x0 + 34, y0 + 74
    for r in range(3):
        for c in range(3):
            px = dx0 + c * 34
            py = dy0 + r * 30
            fill = (214, 69, 61) if (r == 1 and c == 1) else (206, 219, 236)
            d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=fill)


def main():
    img = vgrad((46, 94, 166), (78, 138, 208))
    d = ImageDraw.Draw(img)

    # 顶部日历
    calendar_badge(d, W // 2, 150)

    # 主标题
    f_title = font(FONT_BD, 92)
    title = '考试倒计时'
    tw = d.textlength(title, font=f_title)
    d.text(((W - tw) / 2, 246), title, font=f_title, fill=(255, 255, 255))

    # 副标题
    f_sub = font(FONT_RG, 36)
    sub = '重要考试日期，一个都不漏'
    sw = d.textlength(sub, font=f_sub)
    d.text(((W - sw) / 2, 366), sub, font=f_sub, fill=(214, 230, 250))

    # 底部三个标签
    f_tag = font(FONT_RG, 28)
    tags = ['考试节点', '订阅提醒', '推送不漏']
    pad_x, gap = 34, 22
    widths = [d.textlength(t, font=f_tag) + pad_x * 2 for t in tags]
    total = sum(widths) + gap * (len(tags) - 1)
    x = (W - total) / 2
    for t, w in zip(tags, widths):
        d.rounded_rectangle([x, 462, x + w, 522], radius=30, fill=(255, 255, 255))
        d.text((x + pad_x, 478), t, font=f_tag, fill=(46, 94, 166))
        x += w + gap

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, 'JPEG', quality=88, optimize=True, progressive=True)
    print('saved', OUT, os.path.getsize(OUT), 'bytes', img.size)


if __name__ == '__main__':
    main()
