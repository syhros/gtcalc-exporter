#!/usr/bin/env python3
"""Checks a Pack Extract export folder and builds a contact sheet of sample images.

Usage: check_export.py <export root> <profile: vanilla|gregtech|popular> <contact sheet png>
Works for every Minecraft version the mod supports (the version is read from manifest.json).
Exits non-zero and prints every failed check.
"""
import json
import os
import random
import sys

root, profile, sheet_path = sys.argv[1], sys.argv[2], sys.argv[3]
dirs = [os.path.join(root, d) for d in os.listdir(root) if os.path.isdir(os.path.join(root, d))]
if len(dirs) != 1:
    sys.exit(f"expected one export folder in {root}, found {dirs}")
d = dirs[0]
print("export folder:", d)

failures = []


def check(ok, message):
    print(("ok   " if ok else "FAIL ") + message)
    if not ok:
        failures.append(message)


def load(name, default=None):
    path = os.path.join(d, name)
    if default is not None and not os.path.isfile(path):
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


manifest = load("manifest.json")
mc = manifest["minecraft"]
modern = tuple(int(x) for x in mc.split(".")[:2]) >= (1, 13)
print("minecraft", mc, manifest.get("loader"), "| modern ids" if modern else "| legacy ids (registry:meta)")
print(json.dumps(manifest["counts"], indent=1))
print("stage times (ms):", manifest["stageMillis"])
max_items = manifest.get("maxItems", 0)
items = load("items.json")
fluids = load("fluids.json")
crafting = load("recipes/crafting.json")
smelting = load("recipes/smelting.json")
gregtech = load("recipes/gregtech.json", [])
gt_maps = load("recipes/gregtech-maps.json", [])
other = load("recipes/other.json", [])
tags = load("tags.json", {}) if modern else load("oredict.json")
by_id = {i["id"]: i for i in items}
fluid_by_id = {f["id"]: f for f in fluids}


def item(legacy, modern_id):
    return modern_id if modern else legacy


def fluid(legacy, modern_id):
    return modern_id if modern else legacy


check(len(by_id) == len(items), f"item ids are unique ({len(items)} items)")
nameless = [i["id"] for i in items if not i.get("name")]
# A few mods register items with a blank display name (hidden or technical items).
check(len(nameless) <= max(5, len(items) // 500), f"items have names ({len(nameless)} without, e.g. {nameless[:5]})")
check(manifest["counts"]["items"] == len(items), "manifest item count matches items.json")
check(manifest["counts"].get("errors", 0) < max(50, len(items) // 100),
      f"few errors logged ({manifest['counts'].get('errors', 0)})")

# Images: every item when there is no cap, otherwise maxItems of them.
with_image = [i for i in items if i.get("image")]
expected = min(len(items), max_items) if max_items else len(items)
check(len(with_image) >= expected * 0.98, f"{len(with_image)} item images (expected about {expected})")
missing_files = [i["id"] for i in with_image if not os.path.isfile(os.path.join(d, i["image"]))]
check(not missing_files, f"every item image file exists ({len(missing_files)} missing, e.g. {missing_files[:3]})")
blank = [i["id"] for i in with_image if i.get("imageBlank")]
check(len(blank) / max(1, len(with_image)) < 0.05, f"blank item images under 5% ({len(blank)}, e.g. {blank[:5]})")
fluid_images = [f for f in fluids if f.get("image")]
fluid_expected = min(len(fluids), max_items) if max_items else len(fluids)
check(len(fluid_images) >= fluid_expected * 0.9, f"{len(fluid_images)} fluid images (expected about {fluid_expected})")
mods_drawn = {i["mod"] for i in with_image}
print("mods with images:", sorted(mods_drawn))


def image_stats(rel):
    """(mean brightness 0-255 of visible pixels, number of distinct colours) or None without Pillow."""
    try:
        from PIL import Image
    except ImportError:
        return None
    im = Image.open(os.path.join(d, rel)).convert("RGBA")
    px = [p for p in im.getdata() if p[3] > 128]
    if not px:
        return (0, 0)
    mean = sum((p[0] + p[1] + p[2]) / 3 for p in px) / len(px)
    return (mean, len(set(px)))


def looks_right(rel, label, min_brightness=0, min_colours=4):
    st = image_stats(rel)
    if st is None:
        return
    check(st[0] >= min_brightness and st[1] >= min_colours,
          f"{label} image looks right (brightness {st[0]:.0f} >= {min_brightness}, {st[1]} colours >= {min_colours})")


def item_ok(item_id, label, brightness=None):
    i = by_id.get(item_id)
    check(i is not None, f"{label} ({item_id}) is listed")
    if i and i.get("image"):
        check(not i.get("imageBlank"), f"{label} has a non-blank image")
        if brightness is not None:
            looks_right(i["image"], label, brightness)
    return i


def fluid_ok(fluid_id, label, brightness=None, colours=4):
    f = fluid_by_id.get(fluid_id)
    check(f is not None, f"fluid {label} ({fluid_id}) is listed")
    if f and f.get("image"):
        check(not f.get("imageBlank"), f"fluid {label} has a non-blank image")
        if brightness is not None:
            looks_right(f["image"], label, brightness, colours)


item_ok(item("minecraft:log:0", "minecraft:oak_log"), "Oak Wood", 45)
item_ok(item("minecraft:log:1", "minecraft:spruce_log"), "Spruce Wood")
item_ok(item("minecraft:wool:14", "minecraft:red_wool"), "Red Wool")
item_ok(item("minecraft:iron_ingot:0", "minecraft:iron_ingot"), "Iron Ingot")
fluid_ok(fluid("water", "minecraft:water"), "Water", 40)
fluid_ok(fluid("lava", "minecraft:lava"), "Lava")

if modern:
    logs = tags.get("items", {}).get("minecraft:logs", [])
    check("minecraft:oak_log" in logs, "item tag minecraft:logs contains oak logs")
    check(len(tags.get("fluids", {})) > 0, f"fluid tags exported ({len(tags.get('fluids', {}))})")
else:
    check("logWood" in tags and "minecraft:log:0" in tags["logWood"], "ore dictionary logWood contains oak wood")

table_id = item("minecraft:crafting_table:0", "minecraft:crafting_table")
table = next((r for r in crafting if r.get("output") and r["output"].get("item") == table_id), None)
check(table is not None, "crafting table recipe exported")
if table:
    print("crafting table recipe:", json.dumps(table)[:400])
    check(table["type"] in ("shaped", "shapeless") and any(table["inputs"]), "crafting table recipe has inputs")
iron_ore = item("minecraft:iron_ore:0", "minecraft:iron_ore")
iron_ingot = item("minecraft:iron_ingot:0", "minecraft:iron_ingot")
check(any(r.get("input") and r.get("output") and r["output"].get("item") == iron_ingot
          and (r["input"].get("item") == iron_ore or iron_ore in r["input"].get("anyOf", []))
          for r in smelting), "iron ore smelts to iron ingot")
check(manifest["counts"]["craftingRecipes"] > 200, "more than 200 crafting recipes")

# Every item a recipe mentions is listed.
referenced = set()


def refs_of(x):
    if isinstance(x, dict):
        if x.get("item") and not x["item"].endswith(":*"):
            referenced.add(x["item"])
        for v in x.get("anyOf", []):
            referenced.add(v)


for r in crafting[:20000] + smelting + gregtech[:20000] + other[:20000]:
    for key in ("inputs", "outputs"):
        for x in r.get(key) or []:
            refs_of(x)
    refs_of(r.get("input"))
    refs_of(r.get("output"))
unknown = [i for i in referenced if i not in by_id]
check(not unknown, f"every item a recipe uses is in items.json ({len(unknown)} missing, e.g. {unknown[:5]})")

if other:
    from collections import Counter
    kinds = Counter((r.get("type"), r.get("class")) for r in other)
    print("other.json types and classes (top 20):")
    for (t, c), n in kinds.most_common(20):
        print(f"  {n:6} {t} {c}")

if profile == "gregtech":
    check(len(gt_maps) > 30, f"more than 30 GregTech recipe maps ({len(gt_maps)})")
    check(len(gregtech) > 5000, f"more than 5000 GregTech recipes ({len(gregtech)})")
    mac_id = {"1.7.10": "gt.recipe.macerator", "1.12.2": "macerator"}.get(mc, "gtceu:macerator")
    mac = [r for r in gregtech if r["map"] == mac_id]
    check(len(mac) > 100, f"macerator recipes exported ({len(mac)} in {mac_id})")
    chanced = [r for r in mac if any("chance" in o for o in r["outputs"] if o)]
    check(len(chanced) > 10, f"macerator recipes with chanced outputs ({len(chanced)})")
    if chanced:
        print("chanced macerator recipe:", json.dumps(chanced[0])[:600])
    fluid_recipes = [r for r in gregtech if r["fluidInputs"] or r["fluidOutputs"]]
    check(len(fluid_recipes) > 500, f"recipes with fluids ({len(fluid_recipes)})")
    check(all(isinstance(r["eut"], int) and r["duration"] >= 0 for r in gregtech[:5000]), "eut and duration are numbers")
    gt_drawn = [i for i in with_image if i["mod"] in ("gregtech", "gtceu")]
    check(len(gt_drawn) > 10, f"GregTech items have images ({len(gt_drawn)})")
    gt_blank = [i["id"] for i in gt_drawn if i.get("imageBlank")]
    # GTCEu 7.0 for 1.21 draws a few dynamic-model items (wires, ore indicators) blank; 1.20.1 draws them.
    check(len(gt_blank) <= len(gt_drawn) * 0.08, f"GregTech images are not blank ({len(gt_blank)} of {len(gt_drawn)} blank, e.g. {gt_blank[:6]})")
    if mc == "1.7.10":
        # GT5 meta item ids: prefix * 1000 + material id; Iron is material 32. Correct icons measure about 150
        # (dust), 130 (purified), 120 (crushed) and 80 (impure); icons drawn in a bad renderer state come out ~40.
        for gid, label, floor in [("gregtech:gt.metaitem.01:2032", "Iron Dust", 100),
                                  ("gregtech:gt.metaitem.01:3032", "Impure Pile of Iron Dust", 60),
                                  ("gregtech:gt.metaitem.01:4032", "Purified Pile of Iron Dust", 90),
                                  ("gregtech:gt.metaitem.01:6032", "Purified Crushed Iron Ore", 80)]:
            item_ok(gid, label, floor)
        # GT's molten texture is animated and nearly flat (2-3 colours in some frames): brightness only.
        fluid_ok("molten.iron", "Molten Iron", 60, 1)
        check(manifest["counts"].get("itemsAddedByNei", 0) > 0, "NEI's item list was used (export ran in a world)")
    elif modern:
        item_ok("gtceu:iron_dust", "Iron Dust", 100)
        heated = [r for r in gregtech if r.get("temp")]
        check(len(heated) > 20, f"blast furnace recipes carry their coil temperature ({len(heated)})")

if profile == "popular" and modern:
    # 1.12.2 mods keep machine recipes in their own registries; only 1.13+ has one recipe manager for all.
    check(len(other) > 200, f"other mods' recipes exported ({len(other)} in recipes/other.json)")
    print("other recipe types:", sorted({r["type"] for r in other})[:40])

if mc != "1.7.10":
    print("items added by JEI:", manifest["counts"].get("itemsAddedByJei", 0))

# Contact sheet: the named items first, then a random sample.
try:
    from PIL import Image
    named = [item("minecraft:log:0", "minecraft:oak_log"), item("minecraft:wool:14", "minecraft:red_wool"),
             iron_ingot, item("minecraft:chest:0", "minecraft:chest"), item("minecraft:potion:8193", "minecraft:potion"),
             "gregtech:gt.metaitem.01:2032", "gregtech:gt.metaitem.01:4032", "gtceu:iron_dust", "gtceu:iron_ingot"]
    picks = [by_id[i] for i in named if i in by_id and by_id[i].get("image")]
    random.seed(1)
    picks += random.sample(with_image, min(150, len(with_image)))
    fpicks = [f for f in fluids if f.get("image")][:40]
    cells = [(p["image"], p["id"]) for p in picks] + [(f["image"], "fluid:" + f["id"]) for f in fpicks]
    cols, size = 12, 64
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * size, max(1, rows) * (size + 4)), (40, 40, 48, 255))
    for k, (img, _) in enumerate(cells):
        try:
            im = Image.open(os.path.join(d, img)).convert("RGBA").resize((size, size), Image.NEAREST)
            sheet.alpha_composite(im, ((k % cols) * size, (k // cols) * (size + 4)))
        except Exception as e:
            print("sheet: cannot read", img, e)
    sheet.save(sheet_path)
    with open(sheet_path + ".txt", "w") as f:
        for k, (_, label) in enumerate(cells):
            f.write(f"{k // cols},{k % cols} {label} {by_id.get(label, {}).get('name') or fluid_by_id.get(label[6:], {}).get('name')}\n")
    print("contact sheet:", sheet_path)
except ImportError:
    print("Pillow not installed; no contact sheet")

print()
if failures:
    print(f"{len(failures)} check(s) failed")
    sys.exit(1)
print("all checks passed")
