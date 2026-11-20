"""Собирает картинки справки из игровых текстур: значки предметов, блоков и лиц в стиле подсказки
игры. Каждая картинка 256×256, то есть 2×2 карты в рамках.

Запуск из корня проекта: python tools/help-pictures/draw.py [путь к client-extra.jar]
Текстуры берутся из клиента Minecraft 1.20.1 в кэше ForgeGradle; готовые PNG кладутся
в feature-help/src/main/resources/torturemod/help.
"""
import io
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
OUT = os.path.join("feature-help", "src", "main", "resources", "torturemod", "help")
FONT = "C:/Windows/Fonts/arialbd.ttf" if os.name == "nt" else "DejaVuSans-Bold.ttf"

# Цвета интерфейса игры: фон и рамка подсказки, текст с тенью.
BACK = (16, 0, 16)
BORDER_TOP = (80, 0, 255)
BORDER_BOTTOM = (40, 0, 127)
WHITE = (255, 255, 255)
YELLOW = (255, 255, 85)
GREEN = (85, 255, 85)
RED = (255, 85, 85)
AQUA = (85, 255, 255)
GRAY = (170, 170, 170)

jar = zipfile.ZipFile(JAR)


def texture(name):
    return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + name + ".png"))).convert("RGBA")


def item(name, size=32):
    return texture("item/" + name).resize((size, size), Image.NEAREST)


def block(name, size=32):
    return texture("block/" + name).crop((0, 0, 16, 16)).resize((size, size), Image.NEAREST)


def face(entity, size=32):
    """Лицо с текстуры сущности: голова 8×8 и слой шапки поверх."""
    skin = texture(entity)
    head = skin.crop((8, 8, 16, 16))
    head.alpha_composite(skin.crop((40, 8, 48, 16)))
    return head.resize((size, size), Image.NEAREST)


def handcuffs(size=32):
    """Текстура наручников мода (tools/textures/handcuffs.py)."""
    path = os.path.join("feature-cuffs", "src", "main", "resources", "torturemod", "resourcepack", "handcuffs.png")
    return Image.open(path).convert("RGBA").resize((size, size), Image.NEAREST)


def chest(size=32):
    """Передняя сторона сундука из текстуры блока-сущности: крышка, низ и замок."""
    sheet = texture("entity/chest/normal")
    front = Image.new("RGBA", (14, 15))
    front.paste(sheet.crop((14, 14, 28, 19)), (0, 0))
    front.paste(sheet.crop((14, 33, 28, 43)), (0, 5))
    front.alpha_composite(sheet.crop((1, 1, 3, 5)), (6, 3))
    return front.resize((size, size * 15 // 14), Image.NEAREST)


def font(size):
    return ImageFont.truetype(FONT, size)


def text(draw, x, y, value, color=WHITE, size=14, anchor="la"):
    shadow = tuple(c // 4 for c in color)
    draw.text((x + 2, y + 2), value, fill=shadow, font=font(size), anchor=anchor)
    draw.text((x, y), value, fill=color, font=font(size), anchor=anchor)


def canvas(title, background="stone_bricks"):
    """Фон из плитки блока, поверх — панель-подсказка с заголовком."""
    image = Image.new("RGBA", (256, 256))
    tile = block(background, 32)
    for x in range(0, 256, 32):
        for y in range(0, 256, 32):
            image.paste(tile, (x, y))
    draw = ImageDraw.Draw(image)
    draw.rectangle([6, 6, 249, 249], fill=BACK)
    draw.rectangle([8, 8, 247, 247], outline=BORDER_TOP, width=2)
    draw.line([8, 247, 247, 247], fill=BORDER_BOTTOM, width=2)
    text(draw, 128, 24, title, YELLOW, 19, anchor="mm")
    return image, draw


def row(image, draw, y, icons, lines, color=WHITE):
    """Строка: значки слева, одна-две строки текста справа."""
    x = 16
    for icon in icons:
        image.alpha_composite(icon, (x, y + (32 - icon.height) // 2))
        x += icon.width + 4
    for i, line in enumerate(lines):
        text(draw, x + 4, y + 1 + i * 17, line, color)


def arrow(draw, x, y, length=22, color=WHITE):
    draw.line([x, y, x + length - 6, y], fill=color, width=3)
    draw.polygon([(x + length - 8, y - 6), (x + length - 8, y + 6), (x + length, y)], fill=color)


def save(image, name):
    image.convert("RGB").save(os.path.join(OUT, name))


def marking():
    image, draw = canvas("Разметка тюрьмы")
    stick = item("stick")
    row(image, draw, 44, [stick], ["ЛКМ по блоку —", "первый угол"], RED)
    row(image, draw, 88, [stick], ["ПКМ по блоку —", "второй угол"], AQUA)
    row(image, draw, 132, [stick], ["Shift+ПКМ в воздух —", "создать тюрьму"], GREEN)
    row(image, draw, 176, [stick], ["Shift+ЛКМ —", "сбросить разметку"], GRAY)
    text(draw, 128, 228, "Внутри своей тюрьмы — зона", YELLOW, 13, anchor="mm")
    save(image, "01_prison_marking.png")


def console():
    image, draw = canvas("Пульт и сундуки")
    lectern = block("lectern_front")
    row(image, draw, 44, [item("stick", 24), lectern], ["Shift+ПКМ —", "это пульт"], GREEN)
    row(image, draw, 88, [lectern], ["ПКМ по пульту —", "управление"], AQUA)
    row(image, draw, 132, [item("stick", 24), chest(28)], ["Shift+ПКМ по сундуку", "меняет его роль:"])
    for x, label in zip([16, 70, 150, 204], ["сдача", "награды", "выкуп", "склад"]):
        text(draw, x, 184, label, YELLOW, 13)
    text(draw, 128, 226, "Запасной путь: /torturemod prison", GRAY, 12, anchor="mm")
    save(image, "02_console_and_chests.png")


def cuffs():
    image, draw = canvas("Наручники")
    steve = face("entity/player/wide/steve")
    row(image, draw, 44, [handcuffs(), steve], ["ПКМ ×3 рядом —", "надеть"])
    row(image, draw, 88, [steve], ["Пустая рука ×3 —", "снять (тюремщик)"], GREEN)
    row(image, draw, 132, [item("shears"), steve], ["Ножницы ×5 —", "перекусить"], RED)
    row(image, draw, 176, [item("chain", 24), item("iron_ingot", 24), item("chain", 24)], ["крафт", "наручников"], GRAY)
    text(draw, 128, 228, "В наручниках руками ничего нельзя", GRAY, 12, anchor="mm")
    save(image, "03_cuffs.png")


def escape():
    image, draw = canvas("Побег из тюрьмы")
    bars = block("iron_bars", 24)
    for i in range(4):
        image.alpha_composite(bars, (20, 44 + i * 24))
    image.alpha_composite(face("entity/player/wide/steve"), (66, 72))
    arrow(draw, 106, 88, 40, RED)
    image.alpha_composite(item("ender_eye"), (196, 72))
    text(draw, 16, 148, "Вышел за границы — свободен", WHITE, 13)
    text(draw, 16, 174, "Видит охрана — тревога", AQUA)
    text(draw, 16, 192, "команде тюрьмы", AQUA)
    text(draw, 16, 220, "Никто не видит — тихо", GRAY)
    save(image, "04_escape.png")


def quota():
    image, draw = canvas("Норма труда")
    image.alpha_composite(item("iron_pickaxe"), (20, 46))
    image.alpha_composite(block("cobblestone"), (58, 46))
    arrow(draw, 98, 62)
    image.alpha_composite(chest(32), (126, 44))
    arrow(draw, 166, 62)
    image.alpha_composite(item("bread"), (196, 46))
    text(draw, 16, 94, "Сдай в сундук сдачи")
    text(draw, 16, 112, "за игровой день (20 мин)", GRAY)
    row(image, draw, 138, [item("clock_00", 24)], ["срок короче"], GREEN)
    row(image, draw, 168, [item("golden_apple", 24)], ["силы быстрее"], GREEN)
    row(image, draw, 198, [item("bread", 24)], ["вещи несёт курьер"], GREEN)
    save(image, "05_quota.png")


def ransom():
    image, draw = canvas("Выкуп")
    image.alpha_composite(item("gold_ingot"), (20, 46))
    image.alpha_composite(item("emerald"), (56, 46))
    arrow(draw, 96, 62)
    image.alpha_composite(chest(32), (124, 44))
    arrow(draw, 164, 62)
    image.alpha_composite(face("entity/player/wide/steve"), (194, 46))
    text(draw, 16, 96, "Цену ставит тюрьма", YELLOW)
    text(draw, 16, 122, "ПКМ по сундуку выкупа,")
    text(draw, 16, 140, "выбери пленного,")
    text(draw, 16, 158, "положи цену")
    text(draw, 16, 190, "Тюрьма может", GRAY)
    text(draw, 16, 208, "подтверждать сделки", GRAY)
    save(image, "06_ransom.png")


def minions():
    image, draw = canvas("Мобы тюрьмы")
    zombie = face("entity/zombie/zombie")
    row(image, draw, 44, [item("ender_eye"), zombie], ["на сломленного —", "служит тюрьме"], GREEN)
    row(image, draw, 88, [zombie], ["Shift+ПКМ рукой: пост,", "за мной, курьер"], AQUA)
    row(image, draw, 132, [item("iron_helmet"), item("wooden_sword")], ["ПКМ — надеть", "снаряжение"])
    row(image, draw, 176, [zombie], ["бьёт беглецов", "и пособников"], RED)
    text(draw, 128, 228, "Нежить без шлема горит", GRAY, 13, anchor="mm")
    save(image, "07_minions.png")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for draw_picture in (marking, console, cuffs, escape, quota, ransom, minions):
        draw_picture()
    print("pictures:", sorted(os.listdir(OUT)))
