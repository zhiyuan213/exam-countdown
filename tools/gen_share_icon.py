# -*- coding: utf-8 -*-
"""
生成 hero 区分享按钮图标：绿色「转发」弯箭头（微信转发给朋友同款风格）。
透明底 PNG。输出：miniprogram/images/share-icon.png（覆盖旧的三个点图标）
"""
import os
from PIL import Image, ImageDraw

S = 240          # 画布（超采样，最后缩到 120）
OUT最终 = 120
GREEN = (7, 193, 96, 255)   # 微信绿 #07C160

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images', 'share-icon.png')

# 二次贝塞尔曲线：起点(左下) → 控制点(上方拉平切线) → 终点(右侧)
P0 = (58, 200)
P1 = (52, 88)
P2 = (196, 112)


def bezier(t):
    x = (1 - t) ** 2 * P0[0] + 2 * (1 - t) * t * P1[0] + t ** 2 * P2[0]
    y = (1 - t) ** 2 * P0[1] + 2 * (1 - t) * t * P1[1] + t ** 2 * P2[1]
    return x, y


img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 沿曲线画实心圆 = 粗描边（末端留出箭头位置）
r_stroke = 26
for i in range(41):
    t = i / 40 * 0.86          # 只画到 0.86，后面接箭头
    x, y = bezier(t)
    d.ellipse([x - r_stroke, y - r_stroke, x + r_stroke, y + r_stroke], fill=GREEN)

# 箭头：终点处沿切线方向的三角
t_end = 0.86
x0, y0 = bezier(t_end - 0.02)
x1, y1 = bezier(t_end)
tx, ty = x1 - x0, y1 - y0
import math
L = math.hypot(tx, ty)
tx, ty = tx / L, ty / L          # 单位切线（指向右）

tip = (x1 + tx * 78, y1 + ty * 78)
# 垂直方向
px, py = -ty, tx
base1 = (x1 + px * 52, y1 + py * 52)
base2 = (x1 - px * 52, y1 - py * 52)
d.polygon([tip, base1, base2], fill=GREEN)

img = img.resize((OUT最终, OUT最终), Image.LANCZOS)
img.save(OUT, 'PNG', optimize=True)
print('saved', OUT, os.path.getsize(OUT) // 1024, 'KB')
