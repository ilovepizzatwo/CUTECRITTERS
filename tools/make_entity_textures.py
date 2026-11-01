"""Generates the flat-colour entity textures: the consumed Keeper, the shadow and the five residents.
Usage: python3 tools/make_entity_textures.py src/main/resources"""
import random
import struct
import sys
import zlib

OUT = sys.argv[1] + "/assets/cutecritters/textures/entity/"


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(px[y][x])

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(png)


def blank(w, h, c=(0, 0, 0, 0)):
    return [[c for _ in range(w)] for _ in range(h)]


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y][x] = c


def plot(img, pts, c):
    for (x, y) in pts:
        img[y][x] = c


# ---------------------------------------------------------------- the consumed Keeper (64x32 enderman layout)
random.seed(4242)
k = blank(64, 32)
for y in range(32):
    for x in range(64):
        v = random.randint(2, 11)
        k[y][x] = (v, v, v + 2, 255)

# pale veins crawling over the body and limbs (body 32..56 x 16..32, limbs 56..64 x 0..32)
for _ in range(26):
    x, y = random.randint(33, 62), random.randint(1, 30)
    if 56 > x >= 32 and y < 16:
        continue
    for _ in range(random.randint(4, 11)):
        g = random.randint(46, 82)
        k[y][x] = (g, g, g + 4, 255)
        x = max(32, min(63, x + random.choice((-1, 0, 0, 1))))
        y = max(16 if x < 56 else 0, min(31, y + 1))

# the face: a black void head, two rows of crooked teeth at the bottom
rect(k, 8, 8, 16, 16, (1, 1, 2, 255))
for x in (9, 10, 12, 13, 15):
    k[15][x] = (205, 205, 208, 255)
for x in (8, 11, 14):
    k[14][x] = (150, 150, 155, 255)
# the lower jaw (the "hat" layer): the open mouth shows teeth on top and rot below
rect(k, 8, 24, 16, 32, (2, 2, 3, 255))
for x in (8, 9, 11, 12, 14):
    k[24][x] = (205, 205, 208, 255)
for x in (10, 13, 15):
    k[25][x] = (150, 150, 155, 255)
# hollow cheeks on the sides of the head
for (x0, y0) in ((0, 8), (16, 8)):
    rect(k, x0 + 2, y0 + 2, x0 + 6, y0 + 6, (1, 1, 2, 255))
write_png(OUT + "watcher.png", 64, 32, k)

# glowing eyes: asymmetric on purpose, three on one side and two on the other
e = blank(64, 32)
plot(e, [(9, 9), (10, 9), (9, 11), (11, 12), (10, 13)], (255, 255, 255, 255))
plot(e, [(14, 9), (14, 10), (13, 12)], (255, 255, 255, 255))
write_png(OUT + "watcher_eyes.png", 64, 32, e)

# ---------------------------------------------------------------- the shadow (64x64 humanoid layout)
random.seed(77)
s = blank(64, 64)
for y in range(64):
    for x in range(64):
        v = random.randint(0, 6)
        s[y][x] = (v, v, v + 1, 255)
write_png(OUT + "shadow.png", 64, 64, s)
se = blank(64, 64)
plot(se, [(9, 11), (10, 11), (13, 11), (14, 11)], (255, 255, 255, 255))
write_png(OUT + "shadow_eyes.png", 64, 64, se)

# ---------------------------------------------------------------- residents (64x64 villager layout)
KINDS = [
    # clothes, skin, hair
    ((240, 232, 214), (222, 172, 140), (120, 72, 42)),    # 0 baker
    ((96, 160, 90), (210, 160, 130), (196, 196, 196)),    # 1 gardener
    ((240, 150, 185), (235, 190, 160), (240, 200, 92)),   # 2 child
    ((44, 44, 52), (202, 202, 207), (18, 18, 22)),        # 3 sexton
    ((110, 80, 150), (215, 170, 140), (90, 60, 40)),      # 4 librarian
]
for i, (cloth, skin, hair) in enumerate(KINDS):
    random.seed(100 + i)
    img = blank(64, 64)
    for y in range(64):
        for x in range(64):
            d = random.randint(-6, 6)
            img[y][x] = (max(0, cloth[0] + d), max(0, cloth[1] + d), max(0, cloth[2] + d), 255)
    rect(img, 0, 0, 32, 18, skin + (255,))                 # head
    rect(img, 8, 0, 16, 8, hair + (255,))                  # hair on top
    rect(img, 8, 8, 16, 10, hair + (255,))                 # fringe
    rect(img, 24, 8, 32, 18, hair + (255,))                # back of the head
    rect(img, 0, 8, 8, 11, hair + (255,))
    rect(img, 16, 8, 24, 11, hair + (255,))
    nose = tuple(max(0, c - 22) for c in skin) + (255,)
    rect(img, 24, 0, 32, 6, nose)                          # nose
    if i == 3:
        rect(img, 9, 12, 11, 14, (0, 0, 0, 255))           # hollow eyes
        rect(img, 13, 12, 15, 14, (0, 0, 0, 255))
        rect(img, 11, 16, 13, 17, (60, 60, 66, 255))
    else:
        plot(img, [(9, 12), (14, 12)], (245, 245, 245, 255))
        plot(img, [(10, 12), (13, 12)], (30, 30, 42, 255))
        plot(img, [(11, 16), (12, 16)], (150, 70, 70, 255))
    write_png(OUT + "folk_%d.png" % i, 64, 64, img)

print("entity textures written")
