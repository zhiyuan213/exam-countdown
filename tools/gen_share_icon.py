# -*- coding: utf-8 -*-
"""
生成 hero 区分享按钮图标：经典「三节点分享」图标（两个连线圆点 + 一个左中圆点）。
参考用户提供的样式：连线在左中圆点处汇合，开口朝右，形似「<」。
透明底 PNG，配色蓝 #2C5AA0。输出：miniprogram/images/share-icon.png
"""
import os
from PIL import Image, ImageDraw

S = 480          # 高分辨率画布，最后缩到 120 抗锯齿
OUT_SIZE = 120
BLUE = (44, 90, 160, 255)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-icon.png')

img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 三个节点：左中、右上、右下（「<」形开口朝右）
B = (150, 240)   # 左中（汇合点）
A = (345, 105)   # 右上
C = (345, 375)   # 右下

# 连线（先画线，再画圆点盖住端头）
LW = 46
d.line([B, A], fill=BLUE, width=LW)
d.line([B, C], fill=BLUE, width=LW)

# 圆点
R = 56
for (x, y) in (A, B, C):
    d.ellipse([x - R, y - R, x + R, y + R], fill=BLUE)

img = img.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)
img.save(OUT, 'PNG', optimize=True)
print('saved', os.path.abspath(OUT), os.path.getsize(OUT) // 1024, 'KB')
