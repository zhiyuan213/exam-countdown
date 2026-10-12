# -*- coding: utf-8 -*-
"""
生成 hero 区三个操作按钮图标（120x120 透明底，配色蓝 #2C5AA0）：
  icon-share-friend.png    纸飞机（分享给朋友）
  icon-share-timeline.png  圆环（分享到朋友圈，仿朋友圈入口）
  icon-fav.png             五角星（收藏）
"""
import os
import math
from PIL import Image, ImageDraw

S = 480
OUT_SIZE = 120
BLUE = (44, 90, 160, 255)

IMG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images')


def save(img, name):
    p = os.path.join(IMG_DIR, name)
    img.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS).save(p, 'PNG', optimize=True)
    print('saved', os.path.abspath(p), os.path.getsize(p) // 1024, 'KB')


# 1) 纸飞机（机头右上）
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.polygon([(430, 60), (55, 245), (225, 305), (250, 420)], fill=BLUE)   # 机头/左翼/中折点/下翼
d.line([(430, 60), (225, 305)], fill=(255, 255, 255, 255), width=10)   # 折痕
save(img, 'icon-share-friend.png')

# 2) 圆环（朋友圈光圈意象）
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.ellipse([70, 70, 410, 410], outline=BLUE, width=52)
save(img, 'icon-share-timeline.png')

# 3) 五角星（收藏）
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
cx, cy, R, r = 240, 258, 175, 70
pts = []
for i in range(10):
    rad = math.radians(-90 + i * 36)
    rr = R if i % 2 == 0 else r
    pts.append((cx + rr * math.cos(rad), cy + rr * math.sin(rad)))
d.polygon(pts, fill=BLUE)
save(img, 'icon-fav.png')
