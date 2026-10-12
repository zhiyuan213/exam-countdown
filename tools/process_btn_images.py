# -*- coding: utf-8 -*-
"""
把用户提供的三张拟态按钮图（1080x1080 JPG）处理成小程序按钮底图：
240x240、外圈圆角裁边（PNG 透明角），输出到 miniprogram/images/。
来源：clipboard-2026-10-12T05-34-02-{318Z 星,319Z 光圈,320Z 纸飞机}
"""
import os
from PIL import Image, ImageDraw

SRC_DIR = r'C:/Users/Administrator/.workbuddy/clipboard-images'
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'miniprogram', 'images')

MAP = [
    ('clipboard-2026-10-12T05-34-02-320Z-e25a7e68.jpg', 'btn-friend.png'),     # 纸飞机 → 分享朋友
    ('clipboard-2026-10-12T05-34-02-319Z-4fb26eb2.jpg', 'btn-timeline.png'),   # 光圈 → 分享朋友圈
    ('clipboard-2026-10-12T05-34-02-318Z-bf1edf18.jpg', 'btn-fav.png'),        # 星星 → 收藏
]

SIZE = 240
# 阈值检测分不开柔和光晕与按钮本体（光晕渐变太宽），改用固定中心裁剪 60%，
# 按钮本体约 57%，留少量光晕边；圆角按本体比例取值
CROP = 0.60
RADIUS = 48

for src, dst in MAP:
    im = Image.open(os.path.join(SRC_DIR, src)).convert('RGB')
    w, h = im.size
    cw = int(w * CROP)
    x0 = (w - cw) // 2
    im = im.crop((x0, x0, x0 + cw, x0 + cw)).resize((SIZE, SIZE), Image.LANCZOS)
    mask = Image.new('L', (SIZE, SIZE), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=RADIUS, fill=255)
    out = im.convert('RGBA')
    out.putalpha(mask)
    p = os.path.join(OUT_DIR, dst)
    out.save(p, 'PNG', optimize=True)
    print('saved', os.path.abspath(p), os.path.getsize(p) // 1024, 'KB')
