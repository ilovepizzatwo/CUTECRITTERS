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
END, BYTE, INT, STRING, LIST, COMPOUND = 0, 1, 3, 8, 9, 10


def w_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(t, v):
    if t == INT:
        return struct.pack(">i", v)
    if t == BYTE:
        return struct.pack(">b", v)
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
    def __init__(self, name, sx, sy, sz, ruined=False, seed=1):
        self.name, self.size, self.ruined = name, (sx, sy, sz), ruined
        self.rng = random.Random(seed)
        self.cells = {}

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
            if r.random() < 0.3:
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
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (self.name, name, x, y, z)
        self.cells[(x, y, z)] = (name, props, nbt)

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
                block["nbt"] = (COMPOUND, {k: (STRING, v) for k, v in nbt.items()})
            blocks.append(block)
        root = {
            "DataVersion": (INT, DATA_VERSION),
            "size": (LIST, (INT, list(self.size))),
            "palette": (LIST, (COMPOUND, palette)),
            "blocks": (LIST, (COMPOUND, blocks)),
            "entities": (LIST, (END, [])),
        }
        write_nbt(DATA + "/structures/%s.nbt" % self.name, root)
        return len(blocks)


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

# ---------------------------------------------------------------- worldgen data
W = DATA + "/worldgen/"


def dump(path_, obj):
    os.makedirs(os.path.dirname(path_), exist_ok=True)
    json.dump(obj, open(path_, "w"), indent=2)


def jigsaw_structure(name, biome_tag, spawns=None):
    obj = {
        "type": "minecraft:jigsaw",
        "biomes": "#cutecritters:" + biome_tag,
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": "cutecritters:%s/start" % name,
        "size": 1,
        "start_height": {"absolute": -1},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 40,
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

T = DATA + "/tags/worldgen/biome/"
dump(T + "has_cute_village.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "minecraft:meadow",
                                              "minecraft:sunflower_plains", "#minecraft:is_taiga"]})
dump(T + "has_ruined_village.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "minecraft:meadow",
                                                "#minecraft:is_taiga", "minecraft:swamp", "minecraft:savanna"]})
dump(T + "has_altar.json", {"values": ["#minecraft:is_forest", "minecraft:plains", "#minecraft:is_taiga",
                                       "minecraft:swamp", "minecraft:dark_forest"]})

counts = {
    "cute_village": village("cute_village", False, 11, "cutecritters:chests/village"),
    "ruined_village": village("ruined_village", True, 23, "cutecritters:chests/ruined"),
    "altar": altar(),
}
print("templates (blocks):", counts)
