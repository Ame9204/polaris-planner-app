"""
Generates every Polaris app icon from one definition: a gold four-pointed star on dark.

Run from the repo root (needs Pillow: `pip install pillow`):
    python design/icon/make_icons.py

It writes:
  design/icon/polaris-icon.svg              the full icon as SVG (for reference / other tools)
  design/icon/polaris-icon-1024.png         the full icon, big (stores, previews)
  androidApp/.../drawable-v24/ic_launcher_foreground.xml   star (vector, with the gold gradient)
  androidApp/.../drawable/ic_launcher_background.xml       dark background (vector)
  androidApp/.../drawable/ic_launcher_monochrome.xml       one-colour star (Android 13 themed icons)
  androidApp/.../mipmap-*/ic_launcher(_round).png          PNGs for Android 7 (before adaptive icons)
  desktopApp/icons/polaris.ico / .icns / .png              desktop installer icons

To change the icon: edit the colours or STAR below and run it again.
The design came from design/icon/source-mockup.png (108 x 108, Android's icon grid).
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, 'androidApp', 'src', 'main', 'res')

# ---- The design (108 x 108 grid, like Android's adaptive icon) ----
BACKGROUND = '#1E1E1E'          # dark charcoal
GOLD_LIGHT = '#EDBD37'          # top-left of the star, where the light hits
GOLD_DEEP = '#C9881F'           # bottom-right
GRID = 108
CENTER = 54
TIP = 27                        # star tips are 27 units from the centre (spans 27..81)

# Four points joined by curves that pull in towards the centre (a "sparkle" star)
STAR_PATH = (
    f'M{CENTER},{CENTER - TIP} '
    f'Q{CENTER},{CENTER} {CENTER + TIP},{CENTER} '
    f'Q{CENTER},{CENTER} {CENTER},{CENTER + TIP} '
    f'Q{CENTER},{CENTER} {CENTER - TIP},{CENTER} '
    f'Q{CENTER},{CENTER} {CENTER},{CENTER - TIP} Z'
)


def hex_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def star_points(scale, offset=0.0, steps=40):
    """The star outline as a polygon (each quadratic curve sampled into small straight steps)."""
    c = CENTER * scale + offset
    t = TIP * scale
    tips = [(c, c - t), (c + t, c), (c, c + t), (c - t, c), (c, c - t)]
    pts = []
    for (x0, y0), (x2, y2) in zip(tips, tips[1:]):
        for i in range(steps):
            u = i / steps
            # quadratic Bezier with the control point at the centre
            x = (1 - u) ** 2 * x0 + 2 * (1 - u) * u * c + u ** 2 * x2
            y = (1 - u) ** 2 * y0 + 2 * (1 - u) * u * c + u ** 2 * y2
            pts.append((x, y))
    return pts


def render(size, shape):
    """A finished icon at `size` px. shape: 'square' (rounded), 'circle', or 'full' (no mask)."""
    ss = 4  # draw 4x bigger, then shrink: smooth edges
    big = size * ss
    # The finished icon IS the circle, so the star is sized to the circle (66 units in the
    # mockup), not to the whole 108 grid: it fills ~80% of the circle, like the design.
    scale = big / 66
    offset = big / 2 - CENTER * scale
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))

    # Background in the chosen shape
    bg = Image.new('RGBA', (big, big), hex_rgb(BACKGROUND) + (255,))
    mask = Image.new('L', (big, big), 0)
    md = ImageDraw.Draw(mask)
    if shape == 'circle':
        md.ellipse((0, 0, big - 1, big - 1), fill=255)
    elif shape == 'square':
        md.rounded_rectangle((0, 0, big - 1, big - 1), radius=int(big * 0.22), fill=255)
    else:
        md.rectangle((0, 0, big, big), fill=255)
    img.paste(bg, (0, 0), mask)

    # Gold gradient, top-left (light) to bottom-right (deep), shown through the star shape
    light, deep = hex_rgb(GOLD_LIGHT), hex_rgb(GOLD_DEEP)
    grad = Image.new('RGBA', (big, big))
    gp = grad.load()
    for y in range(big):
        for x in range(big):
            k = (x + y) / (2 * big)
            gp[x, y] = tuple(round(a + (b - a) * k) for a, b in zip(light, deep)) + (255,)
    star_mask = Image.new('L', (big, big), 0)
    ImageDraw.Draw(star_mask).polygon(star_points(scale, offset), fill=255)
    img.paste(grad, (0, 0), star_mask)

    return img.resize((size, size), Image.LANCZOS)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)
    print('wrote', os.path.relpath(path, ROOT))


def save(img, path, **kw):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, **kw)
    print('wrote', os.path.relpath(path, ROOT))


# ---- SVG of the full icon ----
write(os.path.join(ROOT, 'design', 'icon', 'polaris-icon.svg'), f'''<svg xmlns="http://www.w3.org/2000/svg" width="{GRID}" height="{GRID}" viewBox="0 0 {GRID} {GRID}">
  <defs>
    <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{GOLD_LIGHT}"/>
      <stop offset="1" stop-color="{GOLD_DEEP}"/>
    </linearGradient>
  </defs>
  <circle cx="{CENTER}" cy="{CENTER}" r="33" fill="{BACKGROUND}"/>
  <path d="{STAR_PATH}" fill="url(#gold)"/>
</svg>
''')
save(render(1024, 'circle'), os.path.join(ROOT, 'design', 'icon', 'polaris-icon-1024.png'))

# ---- Android adaptive icon (Android 8+): vector layers, sharp at any size ----
write(os.path.join(RES, 'drawable-v24', 'ic_launcher_foreground.xml'), f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/icon/make_icons.py. The gold star, on Android's 108 x 108 icon grid. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="{GRID}"
    android:viewportHeight="{GRID}">
    <path android:pathData="{STAR_PATH}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{CENTER - TIP}"
                android:startY="{CENTER - TIP}"
                android:endX="{CENTER + TIP}"
                android:endY="{CENTER + TIP}"
                android:startColor="#FF{GOLD_LIGHT[1:]}"
                android:endColor="#FF{GOLD_DEEP[1:]}" />
        </aapt:attr>
    </path>
</vector>
''')
write(os.path.join(RES, 'drawable', 'ic_launcher_background.xml'), f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/icon/make_icons.py. Fills the whole icon; the phone cuts it to its shape. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="{GRID}"
    android:viewportHeight="{GRID}">
    <path
        android:fillColor="#FF{BACKGROUND[1:]}"
        android:pathData="M0,0h{GRID}v{GRID}h-{GRID}z" />
</vector>
''')
write(os.path.join(RES, 'drawable', 'ic_launcher_monochrome.xml'), f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by design/icon/make_icons.py. One-colour star for Android 13 "themed icons"
     (the system recolours it to match the wallpaper). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="{GRID}"
    android:viewportHeight="{GRID}">
    <path
        android:fillColor="#FF000000"
        android:pathData="{STAR_PATH}" />
</vector>
''')
for name in ('ic_launcher', 'ic_launcher_round'):
    write(os.path.join(RES, 'mipmap-anydpi-v26', name + '.xml'), '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
''')

# ---- Android 7 (no adaptive icons): ready-made PNGs per screen density ----
for folder, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    save(render(px, 'square'), os.path.join(RES, f'mipmap-{folder}', 'ic_launcher.png'))
    save(render(px, 'circle'), os.path.join(RES, f'mipmap-{folder}', 'ic_launcher_round.png'))

# Any old .webp launcher icons from the project template would clash with the PNGs
for folder in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
    for name in ('ic_launcher.webp', 'ic_launcher_round.webp'):
        p = os.path.join(RES, f'mipmap-{folder}', name)
        if os.path.exists(p):
            os.remove(p)
            print('removed', os.path.relpath(p, ROOT))

# ---- Desktop installers: Windows .ico, macOS .icns, Linux .png ----
icons = os.path.join(ROOT, 'desktopApp', 'icons')
full = render(1024, 'circle')
save(full, os.path.join(icons, 'polaris.ico'), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
save(full, os.path.join(icons, 'polaris.icns'))
save(render(512, 'circle'), os.path.join(icons, 'polaris.png'))
