"""Текстура наручников 16×16 в палитре ванильной цепи: два браслета и звенья между ними.

Запуск из корня проекта: python tools/textures/handcuffs.py
Пишет feature-cuffs/src/main/resources/torturemod/resourcepack/handcuffs.png.
"""
import os

from PIL import Image

OUT = os.path.join("feature-cuffs", "src", "main", "resources", "torturemod", "resourcepack", "handcuffs.png")

# Палитра ванильной цепи и железа: контур, тень, металл, блик.
PALETTE = {
    "o": (39, 42, 51, 255),
    "d": (73, 78, 92, 255),
    "m": (130, 136, 150, 255),
    "l": (190, 196, 208, 255),
    "h": (235, 238, 244, 255),
    ".": (0, 0, 0, 0),
}

# Левый браслет внизу, правый вверху, между ними три звена цепи по диагонали.
PIXELS = [
    "..........oooo..",
    ".........ohllmo.",
    "........ohmddlmo",
    "........olo..olo",
    "........omo..omo",
    ".......oomdooldo",
    "......ommoddddo.",
    ".....oldo.oooo..",
    "..oooomo........",
    ".ohllmoo........",
    "ohmddlmo........",
    "olo..olo........",
    "omo..omo........",
    "omdooldo........",
    ".oddddo.........",
    "..oooo..........",
]


def main():
    image = Image.new("RGBA", (16, 16))
    for y, row in enumerate(PIXELS):
        for x, key in enumerate(row):
            image.putpixel((x, y), PALETTE[key])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("texture:", OUT)


if __name__ == "__main__":
    main()
