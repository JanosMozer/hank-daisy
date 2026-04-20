#!/usr/bin/env python
"""
Generate the Hank & Daisy launcher logo as a PNG, re-creating the teal
badge + cursive script + chrome wing design shown in the reference image
the user posted in chat. Output is saved into the Android drawable folder
so the existing ic_launcher_foreground.xml wrapper picks it up on the
next build.

Run:
    python tools/gen_logo.py
"""

from __future__ import annotations

import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "samples",
    "CameraAccess",
    "app",
    "src",
    "main",
    "res",
    "drawable",
    "ic_launcher_logo.png",
)

# Canvas. Big enough that Android can downscale for every DPI bucket
# without blurring the outline. Square because the adaptive-icon
# foreground viewport is square.
SIZE = 1024

# Palette, sampled visually from the reference logo.
DARK_TEAL = (46, 108, 100, 255)      # outlines + script
MINT_FILL_OUTER = (170, 213, 200, 255)   # outer band of badge
MINT_FILL_INNER = (185, 222, 209, 255)   # inside inner frame
WING_FILL = (150, 206, 189, 255)         # wing body
TRANSPARENT = (0, 0, 0, 0)


def octagon(cx: int, cy: int, half_w: int, half_h: int, chamfer: int) -> list:
    """Return the 8 points of an elongated octagonal badge centered at
    (cx, cy). The chamfer controls how much of the corners is cut."""
    l, r = cx - half_w, cx + half_w
    t, b = cy - half_h, cy + half_h
    c = chamfer
    return [
        (l + c, t),
        (r - c, t),
        (r, t + c),
        (r, b - c),
        (r - c, b),
        (l + c, b),
        (l, b - c),
        (l, t + c),
    ]


def pick_font(paths: list[str], size: int) -> ImageFont.FreeTypeFont:
    """First font that loads wins; default fallback last."""
    for p in paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def draw_logo() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
    d = ImageDraw.Draw(img)

    # ---- Outer badge ----
    badge_cx = SIZE // 2
    badge_cy = 420
    badge_half_w = 440
    badge_half_h = 230
    outer = octagon(badge_cx, badge_cy, badge_half_w, badge_half_h, chamfer=95)
    d.polygon(outer, fill=MINT_FILL_OUTER, outline=DARK_TEAL, width=10)

    # Inner badge (smaller octagon inside, creates the "double frame" look).
    inner_inset = 38
    inner = octagon(
        badge_cx,
        badge_cy,
        badge_half_w - inner_inset,
        badge_half_h - inner_inset,
        chamfer=80,
    )
    d.polygon(inner, fill=MINT_FILL_INNER, outline=DARK_TEAL, width=6)

    # ---- "Hank & Daisy" script ----
    # Brush Script comes closest to the chunky mid-century auto script in
    # the reference. Fall back through other cursive fonts if it's missing.
    font = pick_font(
        [
            r"C:\Windows\Fonts\BRUSHSCI.TTF",
            r"C:\Windows\Fonts\SCRIPTBL.TTF",
            r"C:\Windows\Fonts\segoescb.ttf",
            r"C:\Windows\Fonts\MISTRAL.TTF",
            r"C:\Windows\Fonts\GIGI.TTF",
            r"C:\Windows\Fonts\arialbd.ttf",
        ],
        size=220,
    )
    text = "Hank & Daisy"
    # Size the text to fit inside the inner badge with a comfortable margin.
    max_w = badge_half_w * 2 - 100
    for size in range(240, 100, -10):
        try:
            probe = ImageFont.truetype(font.path, size) if hasattr(font, "path") else font
        except Exception:
            probe = font
        bbox = d.textbbox((0, 0), text, font=probe)
        w = bbox[2] - bbox[0]
        if w <= max_w:
            font = probe
            break

    bbox = d.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = badge_cx - tw // 2 - bbox[0]
    ty = badge_cy - th // 2 - bbox[1] - 10

    d.text((tx, ty), text, fill=DARK_TEAL, font=font)

    # ---- Underline swoosh ----
    # Drawn as a filled lens (two arcs) so it reads as a dynamic crescent
    # under the script rather than a flat bar. Two overlapping ellipse
    # slices — the bottom edge bows up more than the top, producing a
    # leaf-like curve like the reference.
    swoosh_cy = badge_cy + 115
    swoosh_half_w = badge_half_w - 160
    swoosh_rise_top = 26      # how high the top edge bows up at its peak
    swoosh_rise_bot = 44      # bigger rise on the bottom edge → crescent
    # Build the polygon by sampling two arcs.
    N = 40
    top_pts = []
    bot_pts = []
    for i in range(N + 1):
        t = i / N  # 0..1
        x = badge_cx - swoosh_half_w + t * (2 * swoosh_half_w)
        # Parabolic bow, peak at t=0.5.
        bow = 4 * t * (1 - t)
        top_pts.append((x, swoosh_cy - bow * swoosh_rise_top))
        bot_pts.append((x, swoosh_cy + 8 - bow * swoosh_rise_bot))
    lens = top_pts + list(reversed(bot_pts))
    d.polygon(lens, fill=DARK_TEAL)

    # ---- Wing decoration below the badge ----
    # One continuous W/M polygon instead of two disconnected pieces —
    # that's what makes the reference logo's "hood ornament" silhouette
    # read as intentional rather than a pair of floating slabs. The V
    # apex at the bottom and the notch at the top are part of the same
    # outline, closing neatly at the outer tips.
    wing_outer_start = 90           # far-left of outermost wing tip
    wing_outer_end = SIZE - 90      # far-right mirror
    wing_top = 715                  # top edge at the outer tips
    wing_inner_top = 735            # inner-top where top edge dips toward centre
    wing_outer_bot = 820            # bottom edge at the outer tips
    wing_v_apex = 905               # V point at the centre bottom
    v_half_gap = 48                 # horizontal half-width of the top notch

    wing_path = [
        # Start at outer-left top, go right along the top edge with a
        # dip toward the centre notch, then cross to the right side.
        (wing_outer_start + 40, wing_top - 10),             # outer-left top
        (badge_cx - v_half_gap - 60, wing_inner_top),       # top left, near notch
        (badge_cx, wing_inner_top + 40),                     # centre notch apex (top dip)
        (badge_cx + v_half_gap + 60, wing_inner_top),       # top right, near notch
        (wing_outer_end - 40, wing_top - 10),               # outer-right top
        # Down the outer-right tip
        (wing_outer_end, wing_outer_bot - 30),              # outer-right tip
        (wing_outer_end - 60, wing_outer_bot),              # lower-right outer edge
        # Bottom edge sweeping back to centre V
        (badge_cx + v_half_gap + 30, wing_outer_bot - 8),   # bottom-right before V
        (badge_cx, wing_v_apex),                             # V apex
        (badge_cx - v_half_gap - 30, wing_outer_bot - 8),   # bottom-left after V
        # Back to outer-left tip
        (wing_outer_start + 60, wing_outer_bot),            # lower-left outer edge
        (wing_outer_start, wing_outer_bot - 30),            # outer-left tip
    ]
    d.polygon(wing_path, fill=WING_FILL, outline=DARK_TEAL, width=9)

    # Two horizontal "chrome stripe" accents per side, drawn INSIDE the
    # wing polygon so they read as engraved detail at icon size.
    for side_sign in (-1, +1):
        inner_x = badge_cx + side_sign * (v_half_gap + 80)
        tip_x = badge_cx + side_sign * ((SIZE // 2) - 130)
        # Upper stripe follows the slope from notch up to outer tip.
        d.line(
            [(inner_x, wing_inner_top + 20), (tip_x, wing_top + 18)],
            fill=DARK_TEAL,
            width=3,
        )
        # Lower stripe follows the bottom-edge slope.
        d.line(
            [(inner_x, wing_outer_bot - 25), (tip_x, wing_outer_bot - 12)],
            fill=DARK_TEAL,
            width=3,
        )

    return img


def main() -> int:
    img = draw_logo()
    # Anti-alias by rendering at 2x then downscaling — Pillow's default
    # draw lines can look a bit chunky otherwise.
    img.save(OUT, format="PNG", optimize=True)
    print(f"Wrote {OUT}  ({img.size[0]}x{img.size[1]})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
