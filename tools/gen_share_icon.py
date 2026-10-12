# -*- coding: utf-8 -*-
"""
生成 hero 区分享按钮图标：照参考图复刻的「弯钩实心箭头」（粗弧带 + 大三角头）。
透明底 PNG，颜色用 app 配色蓝 #2C5AA0。输出：miniprogram/images/share-icon.png
"""
import os
import math
from PIL import Image, ImageDraw

S = 480          # 高分辨率画布，最后缩到 120 抗锯齿
OUT_SIZE = 120
BLUE = (44, 90, 160, 255)   # 与 hero 标题同款配色蓝

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-icon.png')

img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 1) 尾巴：锥形弧带（头端粗、尾端细，带圆头）。
#    圆心在左上，弧从尾端 92° 扫到头端 28°，形成「左下往上钩、甩向右上」的弯钩
cx, cy, R = 140, 110, 215
A1, A0 = 28, 92          # 头端角 → 尾端角（度）
Wh, Wt = 76, 46          # 头端带宽 / 尾端带宽（锥形收窄）

outer, inner = [], []
N = 60
for i in range(N + 1):
    t = i / N
    a = math.radians(A1 + (A0 - A1) * t)
    w = Wh + (Wt - Wh) * t
    outer.append((cx + (R + w / 2) * math.cos(a), cy + (R + w / 2) * math.sin(a)))
    inner.append((cx + (R - w / 2) * math.cos(a), cy + (R - w / 2) * math.sin(a)))
d.polygon(outer + inner[::-1], fill=BLUE)

# 尾端圆头（小圆，配合锥形收窄不显突兀）
ex0 = cx + R * math.cos(math.radians(A0))
ey0 = cy + R * math.sin(math.radians(A0))
d.ellipse([ex0 - Wt / 2, ey0 - Wt / 2, ex0 + Wt / 2, ey0 + Wt / 2], fill=BLUE)

# 2) 箭头：大实心三角，底边骑在弧带头端（28° 处），沿切线指向右上
ex = cx + R * math.cos(math.radians(A1))
ey = cy + R * math.sin(math.radians(A1))
tx, ty = math.sin(math.radians(A1)), -math.cos(math.radians(A1))   # 切线单位向量
px, py = -ty, tx                                                    # 径向单位向量

HEAD_L, HEAD_HW = 170, 74                   # 头长 / 半底宽（明显宽于尾带，参考图同款比例）
tip = (ex + tx * HEAD_L, ey + ty * HEAD_L)
b1 = (ex + px * HEAD_HW, ey + py * HEAD_HW)
b2 = (ex - px * HEAD_HW, ey - py * HEAD_HW)
d.polygon([tip, b1, b2], fill=BLUE)

img = img.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)
img.save(OUT, 'PNG', optimize=True)
print('saved', os.path.abspath(OUT), os.path.getsize(OUT) // 1024, 'KB')
