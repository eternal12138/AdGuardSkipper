#!/usr/bin/env python3
"""Generate Ciallo launcher icons and splash assets from the two source artworks.

The generated files are committed as Android resources; this script only exists
so the brand assets can be reproduced after replacing the source illustrations.

Usage:
    python tools/generate_brand_assets.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "design"
RES = ROOT / "app" / "src" / "main" / "res"

ICON_SOURCE = DESIGN / "ciallo_icon_source.png"
SPLASH_SOURCE = DESIGN / "ciallo_splash_source.jpg"

SKY_TOP = (188, 237, 255, 255)
SKY_BOTTOM = (236, 250, 255, 255)
CARD = (255, 255, 255, 255)
NAVY = (24, 45, 105, 255)
BLUE = (47, 107, 255, 255)

MIPMAPS = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def load_icon_art() -> Image.Image:
    image = Image.open(ICON_SOURCE).convert("RGBA")
    # The illustration ships with a pure white backdrop.  Trim the empty margin
    # so the character and wordmark stay as large as possible inside the icon.
    rgb = image.convert("RGB")
    mask = Image.eval(rgb, lambda value: 255 if value < 246 else 0)
    mask = mask.convert("L").filter(ImageFilter.MaxFilter(3))
    bbox = mask.getbbox()
    if bbox is None:
        return image
    pad = 8
    left = max(0, bbox[0] - pad)
    top = max(0, bbox[1] - pad)
    right = min(image.width, bbox[2] + pad)
    bottom = min(image.height, bbox[3] + pad)
    art = image.crop((left, top, right, bottom))
    # Keep the artwork's white paper background: the lettering is white with a
    # navy outline, so keying the background out would punch holes in the text.
    return art


def vertical_gradient(size: tuple[int, int], top: tuple[int, int, int, int],
                      bottom: tuple[int, int, int, int]) -> Image.Image:
    width, height = size
    gradient = Image.new("RGBA", (1, height))
    for y in range(height):
        ratio = y / max(1, height - 1)
        gradient.putpixel(
            (0, y),
            tuple(round(top[i] + (bottom[i] - top[i]) * ratio) for i in range(4)),
        )
    return gradient.resize((width, height), Image.Resampling.BILINEAR)


def fit_contain(image: Image.Image, box: tuple[int, int]) -> Image.Image:
    scale = min(box[0] / image.width, box[1] / image.height)
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    return image.resize(size, Image.Resampling.LANCZOS)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255,
    )
    return mask


def make_square_icon(art: Image.Image, size: int) -> Image.Image:
    canvas = vertical_gradient((size, size), SKY_TOP, SKY_BOTTOM)
    canvas = canvas.convert("RGBA")

    card_size = round(size * 0.66)
    card = Image.new("RGBA", (card_size, card_size), CARD)
    card.putalpha(rounded_mask((card_size, card_size), round(card_size * 0.22)))
    # A hairline cobalt edge keeps the white card readable on white launchers.
    edge = Image.new("RGBA", (card_size, card_size), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle(
        (0, 0, card_size - 1, card_size - 1),
        radius=round(card_size * 0.22),
        outline=(47, 107, 255, 100),
        width=max(2, size // 64),
    )
    card = Image.alpha_composite(card, edge)

    inner = fit_contain(art, (round(card_size * 0.91), round(card_size * 0.91)))
    card.alpha_composite(
        inner,
        ((card_size - inner.width) // 2, (card_size - inner.height) // 2),
    )
    canvas.alpha_composite(card, ((size - card_size) // 2, (size - card_size) // 2))
    return canvas


def make_round_icon(art: Image.Image, size: int) -> Image.Image:
    canvas = vertical_gradient((size, size), SKY_TOP, SKY_BOTTOM).convert("RGBA")

    card_size = round(size * 0.66)
    card = Image.new("RGBA", (card_size, card_size), CARD)
    card.putalpha(Image.new("L", (card_size, card_size), 0))
    circle = Image.new("L", (card_size, card_size), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, card_size - 1, card_size - 1), fill=255)
    card.putalpha(circle)

    # Keep the wordmark for the in-app poster and use the character itself in
    # launcher icons so no text is sliced by the platform's circular mask.
    character = art.crop((0, 0, art.width, round(art.height * 0.70)))
    inner = fit_contain(character, (round(card_size * 0.91), round(card_size * 0.91)))
    card.alpha_composite(
        inner,
        ((card_size - inner.width) // 2, (card_size - inner.height) // 2),
    )
    # The illustration brings its own white paper background; clip it back to
    # the circular sticker so no white square corners leak into the blue frame.
    card.putalpha(ImageChops.multiply(card.getchannel("A"), circle))
    canvas.alpha_composite(card, ((size - card_size) // 2, (size - card_size) // 2))

    circle_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(circle_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    canvas.putalpha(circle_mask)
    return canvas


def make_adaptive_foreground(art: Image.Image, size: int = 432) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # 108dp adaptive canvas: the white sticker leaves a cobalt ring inside the
    # 72dp circular safe area, and the illustration still fits without its
    # wordmark being clipped by the launcher mask.
    card_size = round(size * 0.64)
    card = Image.new("RGBA", (card_size, card_size), CARD)
    card.putalpha(rounded_mask((card_size, card_size), round(card_size * 0.23)))
    edge = Image.new("RGBA", (card_size, card_size), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle(
        (0, 0, card_size - 1, card_size - 1),
        radius=round(card_size * 0.23),
        outline=(47, 107, 255, 115),
        width=10,
    )
    card = Image.alpha_composite(card, edge)

    character = art.crop((0, 0, art.width, round(art.height * 0.70)))
    inner = fit_contain(character, (round(card_size * 0.92), round(card_size * 0.92)))
    card.alpha_composite(
        inner,
        ((card_size - inner.width) // 2, (card_size - inner.height) // 2),
    )
    canvas.alpha_composite(card, ((size - card_size) // 2, (size - card_size) // 2))
    return canvas


def make_splash_brand(art: Image.Image, size: int = 512) -> Image.Image:
    """Android 12+ system-splash mark.

    The platform clips this drawable to a small circle, so use the character
    without the wordmark and keep it well inside the icon safe area.  The
    wordmark is already the star of the in-app Compose showcase.
    """
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    character = art.crop((0, 0, art.width, round(art.height * 0.70)))
    inner = fit_contain(character, (round(size * 0.62), round(size * 0.62)))
    canvas.alpha_composite(
        inner,
        ((size - inner.width) // 2, (size - inner.height) // 2),
    )
    return canvas


def make_preview(art: Image.Image) -> Image.Image:
    width, height = 1100, 620
    canvas = vertical_gradient((width, height), (201, 240, 255, 255), (244, 252, 255, 255))
    draw = ImageDraw.Draw(canvas)
    try:
        label_font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 18)
        title_font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 22)
    except OSError:
        label_font = ImageFont.load_default()
        title_font = label_font

    def label(text: str, center_x: int, y: int, font: ImageFont.ImageFont) -> None:
        box = draw.textbbox((0, 0), text, font=font)
        draw.text(
            (center_x - (box[2] - box[0]) / 2, y),
            text,
            fill=(24, 45, 105, 255),
            font=font,
        )

    draw.text((34, 22), "CIALLO LAUNCHER ICONS", fill=(24, 45, 105, 255), font=title_font)
    draw.text((34, 56), "square legacy", fill=(88, 118, 154, 255), font=label_font)
    draw.text((34, 334), "round / adaptive · character crop", fill=(88, 118, 154, 255), font=label_font)

    sizes = (192, 144, 96, 72, 48)
    cell = 200
    for index, size in enumerate(sizes):
        center_x = 55 + index * cell
        icon = make_square_icon(art, size)
        canvas.alpha_composite(icon, (center_x + (192 - size) // 2, 90 + (192 - size) // 2))
        label(f"{size}px", center_x + 96, 306, label_font)

    for index, size in enumerate(sizes):
        center_x = 55 + index * cell
        icon = make_round_icon(art, size)
        canvas.alpha_composite(icon, (center_x + (192 - size) // 2, 368 + (192 - size) // 2))
        label(f"{size}px", center_x + 96, 584, label_font)
    return canvas


def main() -> None:
    art = load_icon_art()
    if art.width == 0 or art.height == 0:
        raise SystemExit("icon source is empty")

    for density, size in MIPMAPS.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        make_square_icon(art, size).save(folder / "ic_launcher.png", optimize=True)
        make_round_icon(art, size).save(folder / "ic_launcher_round.png", optimize=True)

    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    make_adaptive_foreground(art).save(drawable / "ic_launcher_foreground.png", optimize=True)
    make_splash_brand(art).save(drawable / "ic_splash_brand.png", optimize=True)

    with Image.open(SPLASH_SOURCE) as splash:
        splash.convert("RGB").save(
            drawable / "splash_ciallo.jpg", quality=90, optimize=True, progressive=True,
        )

    preview = make_preview(art)
    preview.save(DESIGN / "icon_preview.png", optimize=True)
    print("generated icons, adaptive foreground, splash brand and preview")


if __name__ == "__main__":
    main()
