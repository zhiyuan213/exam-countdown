# -*- coding: utf-8 -*-
"""
生成「分享到朋友圈」分享卡片封面图（1:1，750x750）。
朋友圈卡片的图片显示比例为 1:1（与转发给好友的 5:4 不同），故单独出一张方形图。
输出：miniprogram/images/share-timeline.jpg
"""
import os
from PIL import Image, ImageDraw, ImageFont

S = 750
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-timeline.jpg')

FONT_BD = 'C:/Windows/Fonts/msyhbd.ttc'
FONT_RG = 'C:/Windows/Fonts/msyh.ttc'

TOPBG = (40, 84, 152)
BOTBG = (78, 138, 208)


def font(path, size):
    return ImageFont.truetype(path, size)


def vgrad(top, bottom):
    """纵向渐变底"""
    img = Image.new('RGB', (S, S))
    d = ImageDraw.Draw(img)
    for y in range(S):
        t = y / (S - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (S, y)], fill=c)
    return img


def calendar_badge(d, cx, cy, w, h):
    """白色日历图标 + 红色表头"""
    x0, y0, x1, y1 = cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2
    d.rounded_rectangle([x0, y0, x1, y1], radius=26, fill=(255, 255, 255))
    d.rounded_rectangle([x0, y0, x1, y0 + 54], radius=26, fill=(214, 69, 61))
    d.rectangle([x0, y0 + 34, x1, y0 + 54], fill=(214, 69, 61))
    for bx in (cx - 44, cx + 44):
        d.rounded_rectangle([bx - 10, y0 - 16, bx + 10, y0 + 18], radius=10, fill=(255, 255, 255))
    dx0, dy0 = x0 + 40, y0 + 86
    for r in range(3):
        for c in range(3):
            px = dx0 + c * 40
            py = dy0 + r * 34
            fill = (214, 69, 61) if (r == 1 and c == 1) else (206, 219, 236)
            d.ellipse([px - 7, py - 7, px + 7, py + 7], fill=fill)


def main():
    img = vgrad(TOPBG, BOTBG)
    d = ImageDraw.Draw(img)

    # 顶部日历
    calendar_badge(d, S // 2, 178, 170, 170)

    # 主标题
    f_title = font(FONT_BD, 96)
    title = '考试倒计时'
    tw = d.textlength(title, font=f_title)
    d.text(((S - tw) / 2, 306), title, font=f_title, fill=(255, 255, 255))

    # 副标题
    f_sub = font(FONT_RG, 38)
    sub = '重要考试日期，一个都不漏'
    sw = d.textlength(sub, font=f_sub)
    d.text(((S - sw) / 2, 440), sub, font=f_sub, fill=(214, 230, 250))

    # 三个标签
    f_tag = font(FONT_RG, 30)
    tags = ['考试节点', '订阅提醒', '推送不漏']
    pad_x, gap = 36, 22
    widths = [d.textlength(t, font=f_tag) + pad_x * 2 for t in tags]
    total = sum(widths) + gap * (len(tags) - 1)
    x = (S - total) / 2
    for t, w in zip(tags, widths):
        d.rounded_rectangle([x, 528, x + w, 594], radius=33, fill=(255, 255, 255))
        d.text((x + pad_x, 546), t, font=f_tag, fill=TOPBG)
        x += w + gap

    # 底部一行小字（不带任何诱导文案）
    f_foot = font(FONT_RG, 28)
    foot = '微信小程序 · 考试倒计时'
    fw = d.textlength(foot, font=f_foot)
    d.text(((S - fw) / 2, 662), foot, font=f_foot, fill=(178, 205, 240))

    img.save(OUT, 'JPEG', quality=90, optimize=True)
    print('saved', OUT, os.path.getsize(OUT) // 1024, 'KB')


if __name__ == '__main__':
    main()
