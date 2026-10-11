# -*- coding: utf-8 -*-
"""生成虚拟支付道具图片（200x200，PNG，<200KB）
用法：python gen-quota-icons.py [输出目录]
风格：与「考试倒计时」红色日历品牌色一致的圆角方块，白色大字额度数。
"""
import sys
import os

from PIL import Image, ImageDraw, ImageFont

SIZE = 200
RADIUS = 44  # 圆角，微信展示区域多为圆角卡片
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyhbd.ttc",  # 微软雅黑 Bold
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
]

def font(size):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    raise RuntimeError("未找到中文字体")

def make(path, big, small, top_color, bottom_color):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 垂直渐变背景（圆角矩形）：逐行插值
    for y in range(SIZE):
        t = y / (SIZE - 1)
        r = round(top_color[0] + (bottom_color[0] - top_color[0]) * t)
        g = round(top_color[1] + (bottom_color[1] - top_color[1]) * t)
        b = round(top_color[2] + (bottom_color[2] - top_color[2]) * t)
        d.line([(0, y), (SIZE, y)], fill=(r, g, b, 255))
    # 裁出圆角
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], RADIUS, fill=255)
    img.putalpha(mask)

    d = ImageDraw.Draw(img)
    # 顶部小字（提花装饰）：「考试倒计时」品牌语
    f_tag = font(22)
    tag = "考试倒计时"
    w = d.textlength(tag, font=f_tag)
    d.text(((SIZE - w) / 2, 30), tag, font=f_tag, fill=(255, 255, 255, 230))

    # 大字额度数
    f_big = font(72)
    w = d.textlength(big, font=f_big)
    bbox = d.textbbox((0, 0), big, font=f_big)
    h = bbox[3] - bbox[1]
    d.text(((SIZE - w) / 2, (SIZE - h) / 2 - 12), big, font=f_big, fill=(255, 255, 255, 255))

    # 底部说明
    f_small = font(24)
    w = d.textlength(small, font=f_small)
    d.text(((SIZE - w) / 2, SIZE - 52), small, font=f_small, fill=(255, 255, 255, 220))

    img.save(path, "PNG", optimize=True)
    print(path, os.path.getsize(path), "bytes")

def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    red_top, red_bottom = (255, 106, 100), (229, 72, 77)      # 珊瑚红 → 品牌红
    deep_top, deep_bottom = (255, 141, 74), (240, 98, 46)     # 暖橙 → 深橙（30 次装做区分）
    make(os.path.join(out, "quota10.png"), "+10", "提醒额度", red_top, red_bottom)
    make(os.path.join(out, "quota30.png"), "+30", "提醒额度", deep_top, deep_bottom)

if __name__ == "__main__":
    main()
