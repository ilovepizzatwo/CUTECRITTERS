"""Builds the structure templates (.nbt) and the worldgen JSON for Cute Critters+.

Usage: python3 -I make_structures.py <path to src/main/resources>
"""
import gzip
import json
import math
import os
import random
import struct
import sys

OUT = sys.argv[1]
DATA = OUT + "/data/cutecritters"
DATA_VERSION = 3465  # Minecraft 1.20.1

# ---------------------------------------------------------------- tiny NBT writer
END, BYTE, INT, DOUBLE, STRING, LIST, COMPOUND = 0, 1, 3, 6, 8, 9, 10


def w_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(t, v):
    if t == INT:
        return struct.pack(">i", v)
    if t == BYTE:
        return struct.pack(">b", v)
    if t == DOUBLE:
        return struct.pack(">d", v)
    if t == STRING:
        return w_str(v)
    if t == LIST:
        item_type, items = v
        out = struct.pack(">b", item_type) + struct.pack(">i", len(items))
        for item in items:
            out += payload(item_type, item)
        return out
    if t == COMPOUND:
        out = b""
        for name, (tt, vv) in v.items():
            out += bytes([tt]) + w_str(name) + payload(tt, vv)
        return out + b"\x00"
    raise ValueError(t)


def write_nbt(path, root):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = bytes([COMPOUND]) + w_str("") + payload(COMPOUND, root)
    with gzip.open(path, "wb") as f:
        f.write(data)


# ---------------------------------------------------------------- template builder
class Template:
    def __init__(self, name, sx, sy, sz, ruined=False, seed=1, oy=0):
        self.name, self.size, self.ruined = name, (sx, sy, sz), ruined
        self.oy = oy          # vertical offset added to every y (room for a foundation below)
        self.entities = []
        self.rng = random.Random(seed)
        self.cells = {}
        self.door_gone = {}

    def _ruin(self, name, props, x, y, z):
        """Returns the (name, props) to place instead, or None for 'nothing'."""
        r = self.rng
        if name.endswith("_planks") and y >= 1 and r.random() < 0.26:
            return None
        if name.endswith("glass_pane") and r.random() < 0.8:
            return None
        if name == "lantern" or name == "peony":
            return None
        if name.endswith("_stairs") and r.random() < 0.28:
            return None
        if name.endswith("_door"):
            props = dict(props, open="true")
            key = (x, z)
            if key not in self.door_gone:
                self.door_gone[key] = r.random() < 0.5
            if self.door_gone[key]:
                return None
        if name in ("poppy", "dandelion", "azure_bluet", "cornflower", "pink_tulip", "oxeye_daisy", "allium"):
            return ("dead_bush", {}) if r.random() < 0.5 else None
        if name == "grass_block" and r.random() < 0.35:
            return ("coarse_dirt" if r.random() < 0.7 else "podzol", {})
        return (name, props)

    def set(self, x, y, z, name, nbt=None, **props):
        props = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}
        if self.ruined:
            res = self._ruin(name, props, x, y, z)
            if res is None:
                return
            name, props = res
        y += self.oy
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (self.name, name, x, y, z)
        self.cells[(x, y, z)] = (name, props, nbt)

    def raw(self, x, y, z, name, **props):
        """Place at an absolute y (ignores the offset and the ruin rules): used for foundations."""
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (self.name, name, x, y, z)
        self.cells[(x, y, z)] = (name, {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}, None)

    def entity(self, x, y, z, eid, **tags):
        nbt = {"id": (STRING, eid), "PersistenceRequired": (BYTE, 1)}
        for k, v in tags.items():
            nbt[k] = (INT, v)
        yy = y + self.oy
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= yy < sy and 0 <= z < sz, (self.name, eid, x, yy, z)
        self.entities.append({"pos": (LIST, (DOUBLE, [x + 0.5, float(yy), z + 0.5])),
                              "blockPos": (LIST, (INT, [x, yy, z])),
                              "nbt": (COMPOUND, nbt)})

    def force(self, x, y, z, name, nbt=None, **props):
        """Place without ruin rules."""
        ruined, self.ruined = self.ruined, False
        self.set(x, y, z, name, nbt, **props)
        self.ruined = ruined

    def box(self, x1, y1, z1, x2, y2, z2, name, **props):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **props)

    def air(self, x1, y1, z1, x2, y2, z2):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.force(x, y, z, "air")

    def save(self):
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, nbt) in sorted(self.cells.items()):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": (STRING, "minecraft:" + name)}
                if props:
                    entry["Properties"] = (COMPOUND, {k: (STRING, v) for k, v in props.items()})
                palette.append(entry)
            block = {"state": (INT, index[key]), "pos": (LIST, (INT, [x, y, z]))}
            if nbt:
                block["nbt"] = (COMPOUND, {k: (v if isinstance(v, tuple) else (STRING, v)) for k, v in nbt.items()})
            blocks.append(block)
        root = {
            "DataVersion": (INT, DATA_VERSION),
            "size": (LIST, (INT, list(self.size))),
            "palette": (LIST, (COMPOUND, palette)),
            "blocks": (LIST, (COMPOUND, blocks)),
            "entities": (LIST, (COMPOUND, self.entities)) if self.entities else (LIST, (END, [])),
        }
        write_nbt(DATA + "/structures/%s.nbt" % self.name, root)
        return len(blocks)


class lift:
    """with lift(t, 20): ... builds everything 20 blocks higher."""
    def __init__(self, t, dy):
        self.t, self.dy = t, dy

    def __enter__(self):
        self.t.oy += self.dy

    def __exit__(self, *args):
        self.t.oy -= self.dy


def sign_nbt(key, count=4):
    """A sign whose lines come from the lang file (sign.cutecritters.<key>.<i>).
    A key line written as "=text" is a literal (not translated)."""
    def line(i):
        if isinstance(key, (list, tuple)):
            v = key[i]
            if v.startswith("="):
                return json.dumps({"text": v[1:]}, ensure_ascii=False)
            return json.dumps({"translate": "sign.cutecritters.%s" % v})
        return json.dumps({"translate": "sign.cutecritters.%s.%d" % (key, i)})
    front = {"messages": (LIST, (STRING, [line(i) for i in range(count)])),
             "color": (STRING, "black"), "has_glowing_text": (BYTE, 0)}
    back = {"messages": (LIST, (STRING, [json.dumps({"text": ""}) for _ in range(count)])),
            "color": (STRING, "black"), "has_glowing_text": (BYTE, 0)}
    return {"id": "minecraft:sign", "front_text": (COMPOUND, front), "back_text": (COMPOUND, back),
            "is_waxed": (BYTE, 0)}


def wall_sign(t, x, y, z, key, facing, wood="oak"):
    t.force(x, y, z, wood + "_wall_sign", sign_nbt(key), facing=facing, waterlogged=False)


# ---------------------------------------------------------------- pieces
FLOWERS = ["poppy", "dandelion", "azure_bluet", "cornflower", "pink_tulip", "oxeye_daisy", "allium"]


def roof(t, x0, z0, w, d, y=4, wood="spruce"):
    """A stepped pyramid roof made of stairs, starting with an overhanging ring around the walls."""
    xa, za, xb, zb = x0 - 1, z0 - 1, x0 + w, z0 + d
    stairs, slab, planks = wood + "_stairs", wood + "_slab", wood + "_planks"
    while True:
        wide, deep = xb - xa + 1, zb - za + 1
        if wide <= 2 or deep <= 2:
            t.box(xa, y, za, xb, y, zb, slab, type="bottom")
            return
        for x in range(xa, xb + 1):
            t.set(x, y, za, stairs, facing="south", half="bottom", shape="straight", waterlogged=False)
            t.set(x, y, zb, stairs, facing="north", half="bottom", shape="straight", waterlogged=False)
        for z in range(za + 1, zb):
            t.set(xa, y, z, stairs, facing="east", half="bottom", shape="straight", waterlogged=False)
            t.set(xb, y, z, stairs, facing="west", half="bottom", shape="straight", waterlogged=False)
        for cx, cz in ((xa, za), (xb, za), (xa, zb), (xb, zb)):
            t.set(cx, y, cz, slab, type="bottom", waterlogged=False)
        t.box(xa + 1, y, za + 1, xb - 1, y, zb - 1, planks)
        xa, za, xb, zb, y = xa + 1, za + 1, xb - 1, zb - 1, y + 1


def house(t, x0, z0, w, d, door_x, wood="oak", roof_wood="spruce"):
    planks, log = wood + "_planks", wood + "_log"
    zf = z0 + d - 1  # front wall (south)
    t.air(x0, 1, z0, x0 + w - 1, 3, z0 + d - 1)
    t.box(x0, 0, z0, x0 + w - 1, 0, z0 + d - 1, planks)
    # walls
    for x in range(x0, x0 + w):
        for y in (1, 2, 3):
            t.set(x, y, z0, planks)
            t.set(x, y, zf, planks)
    for z in range(z0, z0 + d):
        for y in (1, 2, 3):
            t.set(x0, y, z, planks)
            t.set(x0 + w - 1, y, z, planks)
    for cx, cz in ((x0, z0), (x0 + w - 1, z0), (x0, zf), (x0 + w - 1, zf)):
        for y in (1, 2, 3):
            t.set(cx, y, cz, log, axis="y")
    # windows (panes connected along the wall)
    for x in (x0 + 2, x0 + w - 3):
        if x != door_x:
            t.set(x, 2, zf, "glass_pane", east=True, west=True, north=False, south=False, waterlogged=False)
        t.set(x, 2, z0, "glass_pane", east=True, west=True, north=False, south=False, waterlogged=False)
    mid = z0 + d // 2
    t.set(x0, 2, mid, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    t.set(x0 + w - 1, 2, mid, "glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)
    # door
    for y in (1, 2, 3):
        t.force(door_x, y, zf, "air")
    t.set(door_x, 1, zf, wood + "_door", facing="south", half="lower", hinge="left", open=False, powered=False)
    t.set(door_x, 2, zf, wood + "_door", facing="south", half="upper", hinge="left", open=False, powered=False)
    t.force(door_x, 3, zf, planks)
    # light and roof
    t.set(x0 + w // 2, 3, mid, "lantern", hanging=True, waterlogged=False)
    roof(t, x0, z0, w, d, 4, roof_wood)


def bed(t, x, z, color="red"):
    t.set(x, 1, z, color + "_bed", facing="north", part="foot", occupied=False)
    t.set(x, 1, z - 1, color + "_bed", facing="north", part="head", occupied=False)


def chest(t, x, y, z, loot, facing="south"):
    t.force(x, y, z, "chest", {"id": "minecraft:chest", "LootTable": loot}, facing=facing, type="single", waterlogged=False)


def path(t, x1, z1, x2, z2):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            t.set(x, 0, z, "dirt_path")


def lamp_post(t, x, z):
    t.set(x, 1, z, "oak_log", axis="y")
    t.set(x, 2, z, "oak_log", axis="y")
    t.set(x, 3, z, "lantern", hanging=False, waterlogged=False)


def garden(t, x1, z1, x2, z2):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            roll = t.rng.random()
            if roll < 0.55:
                t.set(x, 1, z, t.rng.choice(FLOWERS))
            elif roll < 0.62:
                t.set(x, 1, z, "peony", half="lower")
                t.set(x, 2, z, "peony", half="upper")


def well(t, cx, cz):
    t.box(cx - 1, 0, cz - 1, cx + 1, 0, cz + 1, "cobblestone")
    t.force(cx, 0, cz, "water", level=0)
    t.box(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, "cobblestone")
    t.force(cx, 1, cz, "air")
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        t.set(cx + dx, 2, cz + dz, "oak_log", axis="y")
        t.set(cx + dx, 3, cz + dz, "oak_log", axis="y")
    t.box(cx - 1, 4, cz - 1, cx + 1, 4, cz + 1, "spruce_slab", type="bottom", waterlogged=False)


def blood(t):
    """Stains made of red blocks: pools on the floors, a trail outside, marks on the walls."""
    r = t.rng
    houses = ((2, 2, 7, 5), (16, 2, 7, 5), (8, 13, 9, 7))
    for (x0, z0, w, d) in houses:
        for _ in range(4):
            x, z = r.randint(x0 + 1, x0 + w - 2), r.randint(z0 + 1, z0 + d - 2)
            t.force(x, 0, z, "red_terracotta")
            if (x, 1, z) not in t.cells:
                t.force(x, 1, z, "red_carpet")
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if x0 < nx < x0 + w - 1 and z0 < nz < z0 + d - 1 and r.random() < 0.45 and (nx, 1, nz) not in t.cells:
                    t.force(nx, 1, nz, "red_carpet")
        # marks on the outer walls
        for _ in range(5):
            if r.random() < 0.5:
                x, z = r.randint(x0 + 1, x0 + w - 2), r.choice((z0, z0 + d - 1))
            else:
                x, z = r.choice((x0, x0 + w - 1)), r.randint(z0 + 1, z0 + d - 2)
            y = r.choice((1, 2))
            if (x, y, z) in t.cells and t.cells[(x, y, z)][0].endswith("_planks"):
                t.force(x, y, z, "red_terracotta" if r.random() < 0.6 else "nether_wart_block")
    # a trail leading away from the doors, and drops by the well
    for x, z0_, z1_ in ((5, 7, 9), (19, 7, 9), (12, 20, 24)):
        for z in range(z0_, z1_ + 1):
            if r.random() < 0.65 and (x, 1, z) not in t.cells:
                t.force(x, 1, z, "red_carpet")
    for x, z in ((10, 9), (14, 10), (11, 11), (13, 11), (12, 12)):
        if (x, 1, z) not in t.cells and r.random() < 0.7:
            t.force(x, 1, z, "red_carpet")


def village(name, ruined, seed, loot):
    t = Template(name, 25, 10, 25, ruined, seed)
    t.air(0, 1, 0, 24, 9, 24)
    t.box(0, 0, 0, 24, 0, 24, "grass_block")

    house(t, 2, 2, 7, 5, door_x=5)
    house(t, 16, 2, 7, 5, door_x=19)
    house(t, 8, 13, 9, 7, door_x=12)

    # furniture
    bed(t, 3, 5, "red")
    t.set(7, 1, 3, "furnace", facing="south", lit=False)
    t.set(5, 1, 3, "crafting_table")
    bed(t, 17, 5, "blue")
    t.set(21, 1, 3, "bookshelf")
    t.set(21, 2, 3, "bookshelf")
    t.set(19, 1, 3, "crafting_table")
    bed(t, 10, 17, "pink")
    bed(t, 14, 17, "yellow")
    chest(t, 12, 1, 14, loot)
    t.set(9, 1, 14, "barrel", facing="up", open=False)

    well(t, 12, 9)
    path(t, 5, 7, 5, 9)
    path(t, 5, 9, 10, 9)
    path(t, 19, 7, 19, 9)
    path(t, 14, 9, 19, 9)
    path(t, 12, 20, 12, 24)
    garden(t, 2, 11, 6, 22)
    garden(t, 18, 11, 22, 22)
    lamp_post(t, 10, 7)
    lamp_post(t, 14, 7)
    lamp_post(t, 10, 21)
    lamp_post(t, 14, 21)

    if ruined:
        for (x0, z0, w, d) in ((2, 2, 7, 5), (16, 2, 7, 5), (8, 13, 9, 7)):
            for cx, cz in ((x0 + 1, z0 + 1), (x0 + w - 2, z0 + 1), (x0 + 1, z0 + d - 2), (x0 + w - 2, z0 + d - 2)):
                if t.rng.random() < 0.6:
                    t.force(cx, 3, cz, "cobweb")
        blood(t)
        for (x, z) in ((12, 17), (20, 4), (8, 12)):
            t.entity(x, 1, z, "cutecritters:shadow")
    else:
        for (x, z, v) in ((12, 11, 0), (8, 9, 1), (17, 10, 2)):
            t.entity(x, 1, z, "cutecritters:folk", Variant=v)
    return t.save()


def altar():
    t = Template("altar", 21, 9, 21, False, 77)
    t.air(0, 1, 0, 20, 8, 20)
    c = 10
    for x in range(21):
        for z in range(21):
            if math.hypot(x - c, z - c) <= 9.5:
                r = t.rng.random()
                block = "stone_bricks" if r < 0.5 else "mossy_stone_bricks" if r < 0.8 else "cracked_stone_bricks"
                t.set(x, 0, z, block)
    for k in range(8):
        a = k * math.pi / 4
        px, pz = c + round(7 * math.cos(a)), c + round(7 * math.sin(a))
        for y in (1, 2, 3):
            t.set(px, y, pz, "stone_bricks" if y < 3 else "chiseled_stone_bricks")
        t.set(px, 4, pz, "soul_lantern", hanging=False, waterlogged=False)
    t.box(c - 1, 1, c - 1, c + 1, 1, c + 1, "stone_bricks")
    t.set(c, 1, c, "chiseled_stone_bricks")
    t.set(c, 2, c, "cake", bites=0)
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        t.set(c + dx, 2, c + dz, "white_candle", candles=1, lit=True, waterlogged=False)
    chest(t, c, 1, c + 5, "cutecritters:chests/altar", facing="north")
    return t.save()


# ---------------------------------------------------------------- new structures (1.7.0)
LEAF = dict(distance=1, persistent=True, waterlogged=False)
STONES = ("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks")
FOLK, SHADOW = "cutecritters:folk", "cutecritters:shadow"


def pick_stone(r):
    v = r.random()
    return STONES[0] if v < 0.6 else STONES[1] if v < 0.85 else STONES[2]


def bed_at(t, x, y, z, color):
    t.set(x, y, z, color + "_bed", facing="north", part="foot", occupied=False)
    t.set(x, y, z - 1, color + "_bed", facing="north", part="head", occupied=False)


def wall_post(t, x, y, z, block="stone_brick_wall"):
    t.set(x, y, z, block, up=True, north="none", east="none", south="none", west="none", waterlogged=False)


def leaf(t, x, y, z):
    t.set(x, y, z, "oak_leaves", **LEAF)


def tree(t, x, z, h=5):
    for y in range(1, h + 1):
        t.set(x, y, z, "oak_log", axis="y")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (h - 1, h):
                if abs(dx) + abs(dz) <= 3 and not (dx == 0 and dz == 0):
                    leaf(t, x + dx, dy, z + dz)
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        leaf(t, x + dx, h + 1, z + dz)


def dead_tree(t, x, z, h):
    for y in range(1, h + 1):
        t.set(x, y, z, "oak_log", axis="y")
    t.set(x + 1, h - 1, z, "oak_log", axis="x")
    t.set(x + 2, h, z, "oak_log", axis="x")
    t.set(x - 1, h - 2, z, "oak_log", axis="x")
    t.set(x, h - 1, z + 1, "oak_log", axis="z")


def stall(t, x0, z0, color):
    for dx, dz in ((0, 0), (2, 0), (0, 2), (2, 2)):
        t.set(x0 + dx, 1, z0 + dz, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)
        t.set(x0 + dx, 2, z0 + dz, "oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            t.set(x, 3, z, color + "_wool" if (x + z) % 2 == 0 else "white_wool")
    t.set(x0 + 1, 1, z0 + 1, "barrel", facing="up", open=False)


def fence_line(t, cells, gate=None, block="dark_oak_fence"):
    cells = set(cells)

    def conn(c):
        return c in cells or c == gate
    for (x, z) in cells:
        t.set(x, 1, z, block, north=conn((x, z - 1)), south=conn((x, z + 1)),
              east=conn((x + 1, z)), west=conn((x - 1, z)), waterlogged=False)


# ---------------------------------------------------------------- the town
def town():
    oy = 3
    t = Template("critter_town", 41, oy + 15, 31, False, 5, oy)
    r = t.rng
    for x in range(41):
        for z in range(31):
            for y in range(oy):
                t.raw(x, y, z, "dirt")
    t.box(0, 0, 0, 40, 0, 30, "grass_block")
    t.air(0, 1, 0, 40, 13, 30)

    # streets and the plaza
    for x in range(1, 40):
        for z in range(10, 16):
            t.set(x, 0, z, "dirt_path")
        for z in (24, 25):
            t.set(x, 0, z, "dirt_path")
    for x in range(14, 27):
        for z in range(10, 16):
            t.set(x, 0, z, "stone_bricks" if (x + z) % 2 else "polished_andesite")
    for dx in (5, 14, 36):
        t.set(dx, 0, 9, "dirt_path")
    for dx in (5, 15, 26, 35):
        t.set(dx, 0, 23, "dirt_path")

    # houses: north row (doors face the plaza street) and south row
    house(t, 2, 3, 7, 6, 5, "oak", "spruce")
    house(t, 11, 3, 7, 6, 14, "birch", "dark_oak")
    house(t, 20, 2, 11, 8, 25, "cherry", "dark_oak")       # town hall
    house(t, 33, 3, 7, 6, 36, "birch", "spruce")
    house(t, 2, 16, 7, 7, 5, "oak", "spruce")              # bakery
    house(t, 11, 16, 9, 7, 15, "acacia", "dark_oak")
    house(t, 23, 16, 7, 7, 26, "birch", "spruce")
    house(t, 32, 16, 7, 7, 35, "cherry", "dark_oak")

    # interiors
    bed_at(t, 3, 1, 7, "pink")
    t.set(7, 1, 4, "crafting_table")
    t.set(7, 1, 5, "furnace", facing="west", lit=False)
    t.set(4, 1, 4, "bookshelf")
    t.set(5, 1, 4, "bookshelf")
    bed_at(t, 12, 1, 7, "blue")
    t.set(16, 1, 4, "barrel", facing="up", open=False)
    t.set(13, 1, 4, "bookshelf")
    t.set(14, 1, 4, "bookshelf")
    for x in list(range(21, 25)) + list(range(26, 30)):
        t.set(x, 1, 3, "bookshelf")
        t.set(x, 2, 3, "bookshelf")
    chest(t, 25, 1, 3, "cutecritters:chests/town")
    for z in range(4, 9):
        t.set(25, 1, z, "red_carpet")
    t.set(22, 1, 6, "lectern", facing="east", has_book=False, powered=False)
    bed_at(t, 34, 1, 7, "yellow")
    t.set(38, 1, 4, "crafting_table")
    t.set(38, 1, 5, "barrel", facing="up", open=False)
    bed_at(t, 3, 1, 21, "red")
    t.set(7, 1, 17, "smoker", facing="west", lit=False)
    t.set(7, 1, 18, "furnace", facing="west", lit=False)
    t.set(3, 1, 17, "barrel", facing="up", open=False)
    t.set(5, 1, 17, "crafting_table")
    bed_at(t, 12, 1, 21, "blue")
    bed_at(t, 14, 1, 21, "pink")
    chest(t, 18, 1, 17, "cutecritters:chests/village")
    t.set(12, 1, 17, "bookshelf")
    t.set(13, 1, 17, "bookshelf")
    bed_at(t, 24, 1, 21, "green")
    t.set(28, 1, 17, "barrel", facing="up", open=False)
    bed_at(t, 33, 1, 21, "yellow")
    t.set(37, 1, 17, "crafting_table")

    # fountain, stalls, bell
    for x in range(18, 23):
        for z in range(10, 15):
            if x in (18, 22) or z in (10, 14):
                t.set(x, 0, z, "stone_bricks")
                t.set(x, 1, z, "stone_brick_slab", type="bottom", waterlogged=False)
    for x in range(19, 22):
        for z in range(11, 14):
            t.force(x, 0, z, "water", level=0)
    t.set(20, 1, 12, "stone_bricks")
    wall_post(t, 20, 2, 12)
    t.set(20, 3, 12, "lantern", hanging=False, waterlogged=False)
    stall(t, 15, 11, "pink")
    stall(t, 23, 11, "light_blue")
    t.set(20, 1, 15, "stone_bricks")
    t.set(20, 2, 15, "bell", attachment="floor", facing="north", powered=False)

    # lamps, gardens, trees
    for (x, z) in ((13, 10), (27, 10), (13, 15), (27, 15), (8, 23), (20, 23), (30, 23)):
        lamp_post(t, x, z)
    garden(t, 0, 0, 40, 1)
    garden(t, 0, 26, 40, 30)
    for (x, z) in ((6, 28), (34, 28), (20, 28)):
        tree(t, x, z, 5)

    for (x, z, v) in ((4, 24, 0), (16, 14, 1), (24, 14, 0), (22, 15, 2), (28, 12, 2), (13, 24, 1)):
        t.entity(x, 1, z, FOLK, Variant=v)
    return t.save()


# ---------------------------------------------------------------- the underwater grotto
def grotto():
    t = Template("grotto", 25, 13, 39, False, 9)
    r = t.rng
    cx, cz, cy = 12, 25, 2.5
    for x in range(25):
        for z in range(39):
            d = math.hypot(x - cx, z - cz)
            if d <= 11.5:
                for y in (0, 1):
                    t.set(x, y, z, "prismarine_bricks")
                if d <= 8.0:
                    t.set(x, 2, z, "prismarine_bricks" if (x + z) % 2 else "prismarine")
                else:
                    t.set(x, 2, z, "dark_prismarine")
    for x in range(25):
        for y in range(3, 13):
            for z in range(39):
                d3 = math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2)
                if d3 <= 8.0:
                    t.force(x, y, z, "air")
                elif d3 <= 9.3:
                    v = r.random()
                    b = "prismarine_bricks" if v < 0.62 else "prismarine" if v < 0.86 else \
                        "dark_prismarine" if v < 0.93 else "sea_lantern"
                    if y < 4 and b == "sea_lantern":
                        b = "prismarine_bricks"
                    t.set(x, y, z, b)

    # coral and sea pickles around the outside
    corals = ["tube", "brain", "bubble", "fire", "horn"]
    for x in range(25):
        for z in range(39):
            d = math.hypot(x - cx, z - cz)
            if 9.4 < d <= 11.4 and not (x in (11, 12) and z >= 30):
                v = r.random()
                if v < 0.32:
                    t.set(x, 3, z, r.choice(corals) + "_coral_block")
                    if r.random() < 0.5:
                        t.set(x, 4, z, "sea_pickle", pickles=3, waterlogged=True)
                elif v < 0.42:
                    t.set(x, 3, z, "sea_pickle", pickles=r.randint(1, 4), waterlogged=True)

    # the moon pool (the way in) and a water tunnel out to the open sea
    for x in (11, 12):
        for z in (28, 29):
            for y in (0, 1, 2):
                t.force(x, y, z, "water", level=0)
        for z in range(30, 39):
            for y in (0, 1):
                t.force(x, y, z, "water", level=0)
    for (x, z) in ((10, 28), (13, 28), (10, 29), (13, 29)):
        t.set(x, 2, z, "sea_lantern")

    # the cosy room inside
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if dx * dx + dz * dz <= 9:
                t.set(cx + dx, 3, 21 + dz, "pink_carpet")
    chest(t, 12, 3, 19, "cutecritters:chests/grotto")
    for (x, z) in ((9, 21), (15, 21), (12, 24)):
        t.set(x, 3, z, "white_candle", candles=3, lit=True, waterlogged=False)
    bed_at(t, 8, 3, 25, "pink")
    bed_at(t, 16, 3, 25, "light_blue")
    for z in (20, 21):
        t.set(16, 3, z, "bookshelf")
        t.set(16, 4, z, "bookshelf")
    t.set(14, 3, 29, "potted_pink_tulip")
    t.set(9, 3, 30, "potted_azure_bluet")
    for (x, z) in ((10, 19), (14, 19)):
        t.set(x, 3, z, "lantern", hanging=False, waterlogged=False)

    # the wrong room, north of the dome
    for x in range(8, 17):
        for z in range(2, 11):
            for y in range(0, 8):
                if y <= 2 or y == 7:
                    t.set(x, y, z, "deepslate_tiles")
                elif x in (8, 16) or z in (2, 10):
                    t.set(x, y, z, "deepslate_bricks")
                else:
                    t.force(x, y, z, "air")
    for x in range(9, 16):
        for z in range(3, 10):
            if r.random() < 0.4:
                t.set(x, 2, z, "sculk")
    for z in range(10, 16):
        for x in (11, 12, 13):
            for y in range(0, 3):
                t.set(x, y, z, "deepslate_tiles")
            t.set(x, 5, z, "deepslate_tiles")
        for y in (3, 4):
            t.set(11, y, z, "deepslate_bricks")
            t.set(13, y, z, "deepslate_bricks")
    for z in range(10, 18):
        for y in (3, 4):
            t.force(12, y, z, "air")
    t.set(12, 4, 12, "soul_lantern", hanging=True, waterlogged=False)
    chest(t, 12, 3, 3, "cutecritters:chests/grotto_secret")
    wall_sign(t, 12, 4, 3, "grotto", "south", "oak")
    for (x, z) in ((9, 3), (15, 3)):
        t.set(x, 3, z, "black_candle", candles=3, lit=True, waterlogged=False)
    for (x, z) in ((9, 9), (15, 9)):
        t.set(x, 3, z, "soul_lantern", hanging=False, waterlogged=False)
    t.entity(14, 3, 7, SHADOW)
    return t.save()


# ---------------------------------------------------------------- the treehouse
MURALS = {
    "west": ["YYLPLPG", "YYLPPPG", "LLLLPLG"],      # a happy drawing
    "north": ["YYLLLLW", "YYLGLLK", "LLLGLLK"],     # somebody far away
    "east": ["LGLWKWL", "LGLKKKL", "LGLKKKL"],      # somebody close
}
WOOL = {"Y": "yellow_wool", "L": "light_blue_wool", "P": "pink_wool", "G": "lime_wool",
        "W": "white_wool", "K": "black_wool"}


def treehouse():
    oy = 2
    t = Template("treehouse", 31, oy + 33, 31, False, 21, oy)
    r = t.rng
    # canopy first (everything else overwrites it)
    for (bx, by, bz, br) in ((14, 24, 14, 7.0), (7, 22, 11, 4.5), (21, 22, 9, 4.5), (20, 23, 20, 4.5), (8, 22, 20, 4.5)):
        for x in range(31):
            for y in range(19, 31):
                for z in range(31):
                    d = math.sqrt((x - bx) ** 2 + (1.4 * (y - by)) ** 2 + (z - bz) ** 2)
                    if d <= br and r.random() < 0.92:
                        if r.random() < 0.04:
                            t.set(x, y, z, "shroomlight")
                        else:
                            leaf(t, x, y, z)
    # roots and the ground around the trunk
    for x in range(31):
        for z in range(31):
            d = math.hypot(x - 14, z - 14)
            if d <= 5.2:
                for y in range(oy):
                    t.raw(x, y, z, "dirt")
                v = r.random()
                t.set(x, 0, z, "grass_block" if v < 0.6 else "podzol" if v < 0.85 else "coarse_dirt")
    for (px, pz) in ((8, 9), (23, 9), (8, 20), (23, 20)):
        for y in range(oy):
            t.raw(px, y, pz, "dirt")
        for y in range(0, 8):
            t.set(px, y, pz, "spruce_log", axis="y")

    # main platform y=8
    cells = set()
    for x in range(5, 27):
        for z in range(6, 24):
            if (x < 8 or x > 23) and (z < 9 or z > 20):
                continue
            cells.add((x, z))
            t.set(x, 8, z, "spruce_planks")
    for (x, z) in cells:
        edge = any(n not in cells for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))
        if edge:
            t.set(x, 9, z, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)
            if (x + z) % 4 == 0:
                t.set(x, 10, z, "lantern", hanging=False, waterlogged=False)

    # the trunk, the ladder, the crow's nest at y=17
    for x in range(13, 16):
        for z in range(13, 16):
            for y in range(0, 24):
                t.set(x, y, z, "oak_log", axis="y")
    nest = set()
    for x in range(11, 18):
        for z in range(11, 18):
            if (x in (11, 17)) and (z in (11, 17)):
                continue
            nest.add((x, z))
            t.set(x, 17, z, "spruce_planks")
    for (x, z) in nest:
        if any(n not in nest for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))) and (x, z) != (14, 17):
            t.set(x, 18, z, "spruce_fence", north=False, south=False, east=False, west=False, waterlogged=False)
    t.set(11, 19, 12, "lantern", hanging=False, waterlogged=False)
    for y in range(1, 19):
        t.force(14, y, 16, "ladder", facing="south", waterlogged=False)

    # the room (children's room) on the platform
    with lift(t, 8):
        house(t, 16, 10, 11, 9, 21, "spruce", "dark_oak")
        for name, (cells_, ) in (("west", ([(17, 17 - c) for c in range(7)],)),
                                 ("north", ([(18 + c, 11) for c in range(7)],)),
                                 ("east", ([(25, 11 + c) for c in range(7)],))):
            for c, (x, z) in enumerate(cells_):
                for row in range(3):
                    t.force(x, 3 - row, z, WOOL[MURALS[name][row][c]])
        bed_at(t, 19, 1, 17, "pink")
        chest(t, 23, 1, 17, "cutecritters:chests/treehouse", "north")
        for x in range(19, 24):
            for z in range(13, 16):
                t.set(x, 1, z, "white_carpet")
        t.set(22, 1, 12, "potted_dandelion")
        t.set(24, 1, 12, "crafting_table")
        t.entity(21, 1, 14, FOLK, Variant=2)
    return t.save()


# ---------------------------------------------------------------- the underground library
def library():
    t = Template("library", 24, 31, 26, False, 31)
    r = t.rng
    for x in range(0, 23):
        for z in range(3, 24):
            for y in range(0, 8):
                if y == 0:
                    t.set(x, y, z, "dark_oak_planks")
                elif x in (0, 22) or z in (3, 23) or y == 7:
                    t.set(x, y, z, pick_stone(r))
                else:
                    t.force(x, y, z, "air")
    for z in range(5, 23):
        t.set(11, 1, z, "red_carpet")
    for x in (3, 4, 18, 19):
        for z in range(5, 17):
            if z in (10, 11):
                continue
            for y in range(1, 5):
                t.set(x, y, z, "bookshelf")
    for x in range(2, 21):
        for y in range(1, 4):
            t.set(x, y, 4, "bookshelf")
    for y in (1, 2):
        t.set(7, y, 4, "chiseled_bookshelf", facing="south")      # the sixth shelf from the left
    # the hidden niche behind it
    for x in range(5, 10):
        for z in range(0, 3):
            for y in range(0, 5):
                if y in (0, 4) or x in (5, 9) or z == 0:
                    t.set(x, y, z, "deepslate_bricks")
                else:
                    t.force(x, y, z, "air")
    for y in (1, 2):
        t.force(7, y, 3, "air")
    chest(t, 7, 1, 1, "cutecritters:chests/library_secret", "south")
    for x in (6, 8):
        t.set(x, 1, 1, "soul_lantern", hanging=False, waterlogged=False)
        t.set(x, 1, 2, "white_candle", candles=3, lit=True, waterlogged=False)
    wall_sign(t, 7, 3, 2, "niche", "north", "oak")

    # lights, tables, lectern
    for (x, z) in ((7, 8), (15, 8), (7, 18), (15, 18), (11, 7), (11, 15), (11, 21)):
        t.set(x, 6, z, "chain", axis="y", waterlogged=False)
        t.set(x, 5, z, "lantern", hanging=True, waterlogged=False)
    for (x, z) in ((8, 8), (9, 8), (14, 8), (13, 8), (8, 18), (14, 18)):
        t.set(x, 1, z, "spruce_slab", type="top", waterlogged=False)
        t.set(x, 2, z, "white_candle", candles=2, lit=True, waterlogged=False)
    t.set(11, 1, 12, "lectern", facing="north", has_book=False, powered=False)
    for (x, y, z) in ((1, 6, 5), (21, 6, 5), (1, 6, 22), (21, 6, 22), (2, 6, 14), (20, 6, 14)):
        t.set(x, y, z, "cobweb")
    chest(t, 2, 1, 7, "cutecritters:chests/library", "east")
    chest(t, 20, 1, 7, "cutecritters:chests/library", "west")

    # the way down: ladder shaft from a small reading hut on the surface
    t.box(15, 16, 17, 21, 19, 23, "dirt")
    t.box(17, 8, 19, 19, 19, 21, "stone_bricks")
    for y in range(1, 7):
        t.set(18, y, 19, "stone_bricks")
    for y in range(1, 22):
        t.force(18, y, 20, "ladder", facing="south", waterlogged=False)
    with lift(t, 20):
        house(t, 15, 17, 7, 7, 18, "oak", "spruce")
        t.force(18, 0, 20, "ladder", facing="south", waterlogged=False)
        t.force(18, 1, 20, "ladder", facing="south", waterlogged=False)
        t.force(18, 1, 19, "bookshelf")
        t.force(18, 2, 19, "bookshelf")
        for z in (18, 19):
            t.set(16, 1, z, "bookshelf")
        t.set(20, 1, 18, "bookshelf")
        t.set(20, 1, 22, "bookshelf")
        t.set(16, 1, 22, "crafting_table")
        wall_sign(t, 16, 1, 24, "library", "south", "oak")
    t.entity(11, 1, 14, FOLK, Variant=4)
    t.entity(2, 1, 20, SHADOW)
    return t.save()


# ---------------------------------------------------------------- the graveyard
GRAVES = {
    (5, 11): "pushok", (8, 11): "oblachko", (17, 11): "iskorka", (20, 11): "svetik",
    (5, 15): "laska", (8, 15): "ryzhik", (17, 15): "blank", (20, 15): "blank",
    (5, 19): "blank", (8, 19): "blank", (17, 19): "fresh", (20, 19): "blank",
}


def graveyard():
    oy = 2
    t = Template("graveyard", 27, oy + 10, 25, False, 41, oy)
    r = t.rng
    for x in range(27):
        for z in range(25):
            for y in range(oy):
                t.raw(x, y, z, "dirt")
            v = r.random()
            t.set(x, 0, z, "grass_block" if v < 0.5 else "coarse_dirt" if v < 0.8 else "podzol")
    t.air(0, 1, 0, 26, 9, 24)

    # fence with a gate on the south side
    cells = set()
    for x in range(1, 26):
        cells.add((x, 1))
        cells.add((x, 23))
    for z in range(1, 24):
        cells.add((1, z))
        cells.add((25, z))
    gate = (13, 23)
    cells.discard(gate)
    fence_line(t, cells, gate)
    t.set(13, 1, 23, "dark_oak_fence_gate", facing="south", in_wall=False, open=False, powered=False)
    for x in (12, 14):
        t.set(x, 2, 23, "dark_oak_fence", north=False, south=False, east=False, west=False, waterlogged=False)
        t.set(x, 3, 23, "lantern", hanging=False, waterlogged=False)
    for z in range(5, 23):
        for x in (12, 13, 14):
            t.set(x, 0, z, "dirt_path")
    for x in range(14, 21):
        t.set(x, 0, 8, "dirt_path")

    # the old tomb at the end of the path
    t.box(12, 1, 3, 14, 1, 3, "chiseled_stone_bricks")
    wall_post(t, 13, 2, 3)
    t.box(12, 1, 2, 14, 1, 2, "coarse_dirt")
    for x in (11, 15):
        t.set(x, 1, 3, "soul_lantern", hanging=False, waterlogged=False)
    for x in (12, 14):
        t.set(x, 2, 3, "white_candle", candles=2, lit=True, waterlogged=False)
    wall_sign(t, 13, 1, 4, "keeper", "south", "oak")

    # the graves
    flowers = ["poppy", "dandelion", "allium", "azure_bluet"]
    for (gx, gz), sid in GRAVES.items():
        t.set(gx, 1, gz, pick_stone(r))
        t.set(gx, 2, gz, "stone_brick_slab", type="bottom", waterlogged=False)
        mound = "dirt" if sid == "fresh" else "coarse_dirt"
        t.set(gx, 1, gz - 1, mound)
        t.set(gx, 1, gz - 2, mound)
        if sid == "fresh":
            for dx in (-1, 0, 1):
                t.set(gx + dx, 2, gz - 1, r.choice(flowers))
            t.set(gx + 1, 1, gz + 1, "soul_lantern", hanging=False, waterlogged=False)
            wall_sign(t, gx, 1, gz + 1, ["fresh.0", "=???", "fresh.2", "fresh.3"], "south", "oak")
        else:
            if r.random() < 0.6:
                t.set(gx, 2, gz - 1, r.choice(flowers))
            wall_sign(t, gx, 1, gz + 1, sid, "south", "oak")

    # the keeper's hut
    house(t, 17, 2, 7, 6, 20, "spruce", "dark_oak")
    bed_at(t, 18, 1, 6, "red")
    chest(t, 22, 1, 3, "cutecritters:chests/graveyard")
    t.set(22, 1, 4, "barrel", facing="up", open=False)
    t.set(18, 1, 3, "soul_lantern", hanging=False, waterlogged=False)

    for (x, z, h) in ((3, 3, 6), (23, 20, 5), (3, 20, 5), (9, 5, 6)):
        dead_tree(t, x, z, h)
    for (x, z) in ((12, 13), (14, 17)):
        t.set(x, 1, z, "soul_lantern", hanging=False, waterlogged=False)
    t.entity(14, 1, 8, FOLK, Variant=3)
    return t.save()


# ---------------------------------------------------------------- loot tables
def note_entry(n, weight):
    return {"type": "minecraft:item", "name": "cutecritters:lore_note", "weight": weight,
            "functions": [{"function": "minecraft:set_nbt", "tag": "{Note:%d}" % n}]}


def item_entry(name, weight, lo=1, hi=1):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi > 1:
        entry["functions"] = [{"function": "minecraft:set_count",
                               "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return entry


def loot(path_, entries, rolls_min, rolls_max):
    os.makedirs(os.path.dirname(path_), exist_ok=True)
    json.dump({"type": "minecraft:chest", "pools": [{
        "rolls": {"type": "minecraft:uniform", "min": rolls_min, "max": rolls_max},
        "entries": entries}]}, open(path_, "w"), indent=2)


L = DATA + "/loot_tables/chests/"
loot(L + "village.json", [
    item_entry("cutecritters:critter_cookie", 6, 1, 3), item_entry("minecraft:sweet_berries", 4, 2, 5),
    item_entry("minecraft:bread", 4, 1, 3), item_entry("minecraft:white_wool", 3, 1, 3),
    item_entry("minecraft:glow_berries", 2, 1, 3), note_entry(0, 3), note_entry(1, 2), note_entry(2, 1)], 3, 5)
loot(L + "ruined.json", [
    item_entry("minecraft:string", 4, 1, 3), item_entry("minecraft:bone", 3, 1, 2),
    item_entry("minecraft:rotten_flesh", 3, 1, 3), item_entry("cutecritters:critter_cookie", 3, 1, 2),
    note_entry(1, 3), note_entry(2, 3), note_entry(3, 3), note_entry(4, 1)], 3, 5)
loot(L + "altar.json", [
    item_entry("cutecritters:critter_cookie", 5, 2, 4), item_entry("minecraft:golden_apple", 1),
    item_entry("minecraft:white_candle", 3, 1, 3), note_entry(3, 3), note_entry(4, 6), note_entry(5, 6)], 4, 6)

loot(L + "town.json", [
    item_entry("cutecritters:critter_cookie", 6, 1, 3), item_entry("minecraft:bread", 4, 1, 3),
    item_entry("minecraft:sweet_berries", 4, 2, 5), item_entry("minecraft:honey_bottle", 2),
    note_entry(0, 3), note_entry(1, 2), note_entry(2, 2)], 3, 5)
loot(L + "treehouse.json", [
    item_entry("cutecritters:critter_cookie", 5, 1, 2), item_entry("minecraft:apple", 3, 1, 3),
    item_entry("minecraft:dandelion", 3, 1, 3), item_entry("minecraft:honey_bottle", 1),
    note_entry(8, 7), note_entry(0, 2), note_entry(1, 2)], 3, 4)
loot(L + "library.json", [
    item_entry("minecraft:paper", 5, 1, 4), item_entry("minecraft:book", 3, 1, 2),
    item_entry("cutecritters:critter_cookie", 3, 1, 2), item_entry("minecraft:white_candle", 2, 1, 3),
    note_entry(6, 7), note_entry(3, 3), note_entry(4, 3), note_entry(5, 3)], 4, 6)
loot(L + "library_secret.json", [note_entry(9, 1)], 1, 1)
loot(L + "grotto.json", [
    item_entry("cutecritters:critter_cookie", 4, 1, 3), item_entry("minecraft:nautilus_shell", 2),
    item_entry("minecraft:prismarine_crystals", 4, 1, 3), item_entry("minecraft:prismarine_shard", 3, 1, 4),
    item_entry("minecraft:heart_of_the_sea", 1), note_entry(3, 3), note_entry(4, 3)], 3, 5)
loot(L + "grotto_secret.json", [
    note_entry(7, 7), note_entry(5, 3), item_entry("cutecritters:critter_cookie", 2, 1, 2),
    item_entry("minecraft:soul_lantern", 1)], 2, 3)
loot(L + "graveyard.json", [
    item_entry("minecraft:bone", 3, 1, 3), item_entry("minecraft:white_candle", 3, 1, 2),
    item_entry("minecraft:bone_meal", 3, 1, 3), item_entry("minecraft:poppy", 3, 1, 4),
    item_entry("cutecritters:critter_cookie", 3, 1, 2),
    note_entry(7, 4), note_entry(8, 3), note_entry(2, 3)], 3, 5)

# ---------------------------------------------------------------- worldgen data
W = DATA + "/worldgen/"


def dump(path_, obj):
    os.makedirs(os.path.dirname(path_), exist_ok=True)
    json.dump(obj, open(path_, "w"), indent=2)


def jigsaw_structure(name, biome_tag, spawns=None, start=-1, heightmap="WORLD_SURFACE_WG", adapt="beard_thin"):
    obj = {
        "type": "minecraft:jigsaw",
        "biomes": "#cutecritters:" + biome_tag,
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": adapt,
        "start_pool": "cutecritters:%s/start" % name,
        "size": 1,
        "start_height": {"absolute": start},
        "project_start_to_heightmap": heightmap,
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
    }
    if spawns:
        obj["spawn_overrides"] = {"creature": {"bounding_box": "full", "spawns": spawns}}
    dump(W + "structure/%s.json" % name, obj)
    dump(W + "template_pool/%s/start.json" % name, {
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element",
            "location": "cutecritters:" + name,
            "processors": "minecraft:empty", "projection": "rigid"}}]})


def structure_set(name, spacing, separation, salt):
    dump(W + "structure_set/%s.json" % name, {
        "structures": [{"structure": "cutecritters:" + name, "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": spacing,
                      "separation": separation, "salt": salt}})


critters = [{"type": "cutecritters:" + c, "weight": w, "minCount": 1, "maxCount": m}
            for c, w, m in (("fox_cub", 4, 2), ("cloud_sheep", 3, 2), ("weasel", 2, 1), ("firefly", 3, 2))]

jigsaw_structure("cute_village", "has_cute_village", critters)
jigsaw_structure("ruined_village", "has_ruined_village")
jigsaw_structure("altar", "has_altar")
structure_set("cute_village", 40, 14, 1472581)
structure_set("ruined_village", 38, 14, 2468013)
structure_set("altar", 52, 18, 3590127)

jigsaw_structure("critter_town", "has_critter_town", critters, start=-4)
jigsaw_structure("grotto", "has_grotto", None, start=-1, heightmap="OCEAN_FLOOR_WG", adapt="none")
jigsaw_structure("treehouse", "has_treehouse", critters, start=-3)
jigsaw_structure("library", "has_library", None, start=-21, adapt="none")
jigsaw_structure("graveyard", "has_graveyard", None, start=-3)
structure_set("critter_town", 36, 12, 4180231)
structure_set("grotto", 32, 10, 5390772)
structure_set("treehouse", 30, 10, 6102983)
structure_set("library", 34, 12, 7214594)
structure_set("graveyard", 28, 10, 8326105)

T = DATA + "/tags/worldgen/biome/"
dump(T + "has_cute_village.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "minecraft:meadow",
                                              "minecraft:sunflower_plains", "#minecraft:is_taiga"]})
dump(T + "has_ruined_village.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "minecraft:meadow",
                                                "#minecraft:is_taiga", "minecraft:swamp", "minecraft:savanna"]})
dump(T + "has_critter_town.json", {"values": ["minecraft:plains", "minecraft:meadow", "minecraft:sunflower_plains",
                                              "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest"]})
dump(T + "has_grotto.json", {"values": ["#minecraft:is_ocean"]})
dump(T + "has_treehouse.json", {"values": ["#minecraft:is_forest"]})
dump(T + "has_library.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "minecraft:meadow",
                                         "minecraft:sunflower_plains", "#minecraft:is_taiga"]})
dump(T + "has_graveyard.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "#minecraft:is_taiga",
                                           "minecraft:swamp"]})
dump(T + "has_altar.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "#minecraft:is_taiga",
                                       "minecraft:swamp", "minecraft:dark_forest"]})

counts = {
    "cute_village": village("cute_village", False, 11, "cutecritters:chests/village"),
    "ruined_village": village("ruined_village", True, 23, "cutecritters:chests/ruined"),
    "altar": altar(),
    "critter_town": town(),
    "grotto": grotto(),
    "treehouse": treehouse(),
    "library": library(),
    "graveyard": graveyard(),
}
print("templates (blocks):", counts)
