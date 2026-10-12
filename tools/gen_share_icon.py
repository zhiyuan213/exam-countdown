# -*- coding: utf-8 -*-
"""
生成 hero 区圆形分享按钮用的图标（经典三点连接 share 图标）。
透明底 PNG，主色 #2C5AA0，显示尺寸约 40rpx。
输出：miniprogram/images/share-icon.png
"""
import os
from PIL import Image, ImageDraw

S = 240  # 画布（2x 超采样抗锯齿后缩回 120）
COLOR = (44, 90, 160, 255)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-icon.png')

img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

R = 22  # 节点半径
nodes = [(52, 120), (172, 48), (172, 192)]

# 连接线（先画线再画点，节点圆盖住线头）
lw = 20
for i in range(len(nodes) - 1):
    x0, y0 = nodes[i]
    x1, y1 = nodes[i + 1]
    d.line([(x0, y0), (x1, y1)], fill=COLOR, width=lw)

for (x, y) in nodes:
    d.ellipse([x - R, y - R, x + R, y + R], fill=COLOR)

img = img.resize((120, 120), Image.LANCZOS)
img.save(OUT, 'PNG', optimize=True)
print('saved', OUT, os.path.getsize(OUT) // 1024, 'KB')
