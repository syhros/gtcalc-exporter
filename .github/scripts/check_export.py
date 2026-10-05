#!/usr/bin/env python3
"""Checks a Pack Extract export folder and builds a contact sheet of sample images.

Usage: check_export.py <export root> <profile: vanilla|gregtech> <contact sheet png>
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


def load(name):
    with open(os.path.join(d, name), encoding="utf-8") as f:
        return json.load(f)


manifest = load("manifest.json")
print(json.dumps(manifest["counts"], indent=1))
print("stage times (ms):", manifest["stageMillis"])
items = load("items.json")
fluids = load("fluids.json")
crafting = load("recipes/crafting.json")
smelting = load("recipes/smelting.json")
gregtech = load("recipes/gregtech.json")
gt_maps = load("recipes/gregtech-maps.json")
oredict = load("oredict.json")
by_id = {i["id"]: i for i in items}
fluid_by_id = {f["id"]: f for f in fluids}

check(len(by_id) == len(items), f"item ids are unique ({len(items)} items)")
check(all(i.get("name") for i in items[:5000]), "items have names")
check(manifest["counts"]["items"] == len(items), "manifest item count matches items.json")

# Images
with_image = [i for i in items if i.get("image")]
check(len(with_image) / max(1, len(items)) > 0.98, f"{len(with_image)}/{len(items)} items have an image")
missing_files = [i["id"] for i in with_image if not os.path.isfile(os.path.join(d, i["image"]))]
check(not missing_files, f"every item image file exists ({len(missing_files)} missing, e.g. {missing_files[:3]})")
blank = [i["id"] for i in items if i.get("imageBlank")]
check(len(blank) / max(1, len(items)) < 0.05, f"blank item images under 5% ({len(blank)}, e.g. {blank[:5]})")
fluid_images = [f for f in fluids if f.get("image")]
check(len(fluid_images) / max(1, len(fluids)) > 0.9, f"{len(fluid_images)}/{len(fluids)} fluids have an image")


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


def item_ok(item_id, label):
    i = by_id.get(item_id)
    check(i is not None, f"{label} ({item_id}) is listed")
    if i:
        check(bool(i.get("image")) and not i.get("imageBlank"), f"{label} has a non-blank image")
    return i


def fluid_ok(fluid_id, label):
    f = fluid_by_id.get(fluid_id)
    check(f is not None, f"fluid {label} ({fluid_id}) is listed")
    if f:
        check(bool(f.get("image")) and not f.get("imageBlank"), f"fluid {label} has a non-blank image")


oak = item_ok("minecraft:log:0", "Oak Wood")
if oak and oak.get("image"):
    looks_right(oak["image"], "Oak Wood", 45)
item_ok("minecraft:log:1", "Spruce Wood")
item_ok("minecraft:wool:14", "Red Wool")
item_ok("minecraft:iron_ingot:0", "Iron Ingot")
fluid_ok("water", "Water")
if fluid_by_id.get("water", {}).get("image"):
    looks_right(fluid_by_id["water"]["image"], "Water", 40)
fluid_ok("lava", "Lava")
check("logWood" in oredict and "minecraft:log:0" in oredict["logWood"], "ore dictionary logWood contains oak wood")


def outputs_of(r):
    o = r.get("output")
    return [o] if o else []


check(any(r.get("output") and r["output"].get("item") == "minecraft:crafting_table:0" for r in crafting),
      "crafting table recipe exported")
table = next((r for r in crafting if r.get("output") and r["output"].get("item") == "minecraft:crafting_table:0"), None)
if table:
    print("crafting table recipe:", json.dumps(table)[:400])
    check(table["type"] in ("shaped", "shapeless") and any(table["inputs"]), "crafting table recipe has inputs")
# Vanilla registers block smelting for any variant: the input is "minecraft:iron_ore:*" with anyOf.
check(any(r["input"] and r["output"] and r["output"].get("item") == "minecraft:iron_ingot:0"
          and (r["input"].get("item") == "minecraft:iron_ore:0" or "minecraft:iron_ore:0" in r["input"].get("anyOf", []))
          for r in smelting), "iron ore smelts to iron ingot")
check(manifest["counts"]["craftingRecipes"] > 200, "more than 200 crafting recipes")

if profile == "gregtech":
    # GT5 meta item ids: prefix * 1000 + material id; Iron is material 32.
    # Iron is light grey. Icons drawn while the renderer was in a bad state came out near-black (brightness ~40);
    # correct ones measure about 150 (dust), 130 (purified), 120 (crushed) and 80 (impure, a dirtier texture).
    for gid, label, floor in [("gregtech:gt.metaitem.01:2032", "Iron Dust", 100),
                              ("gregtech:gt.metaitem.01:3032", "Impure Pile of Iron Dust", 60),
                              ("gregtech:gt.metaitem.01:4032", "Purified Pile of Iron Dust", 90),
                              ("gregtech:gt.metaitem.01:6032", "Purified Crushed Iron Ore", 80)]:
        it = item_ok(gid, label)
        if it and it.get("image"):
            looks_right(it["image"], label, floor)
    fluid_ok("molten.iron", "Molten Iron")
    if fluid_by_id.get("molten.iron", {}).get("image"):
        # GT's molten texture is animated and nearly flat (2-3 colours in some frames): brightness only.
        looks_right(fluid_by_id["molten.iron"]["image"], "Molten Iron", 60, 1)
    check(manifest["counts"].get("itemsAddedByNei", 0) > 0, "NEI's item list was used (export ran in a world)")
    check(len(gregtech) > 10000, f"more than 10000 GregTech recipes ({len(gregtech)})")
    check(len(gt_maps) > 50, f"more than 50 GregTech recipe maps ({len(gt_maps)})")
    mac = [r for r in gregtech if r["map"] == "gt.recipe.macerator"]
    check(len(mac) > 100, f"macerator recipes exported ({len(mac)})")
    chanced = [r for r in mac if any("chance" in o for o in r["outputs"] if o)]
    check(len(chanced) > 10, f"macerator recipes with chanced outputs ({len(chanced)})")
    if chanced:
        print("chanced macerator recipe:", json.dumps(chanced[0])[:600])
    fluid_recipes = [r for r in gregtech if r["fluidInputs"] or r["fluidOutputs"]]
    check(len(fluid_recipes) > 1000, f"recipes with fluids ({len(fluid_recipes)})")
    referenced = set()
    for r in gregtech[:20000]:
        for x in r["inputs"] + r["outputs"]:
            if x and x.get("item") and not x["item"].endswith(":*"):
                referenced.add(x["item"])
    unknown = [i for i in referenced if i not in by_id]
    check(not unknown, f"every item a GregTech recipe uses is in items.json ({len(unknown)} missing, e.g. {unknown[:5]})")
    check(all(isinstance(r["eut"], int) and r["duration"] >= 0 for r in gregtech[:5000]), "eut and duration are numbers")

# Contact sheet: the named items first, then a random sample.
try:
    from PIL import Image, ImageDraw
    named = ["minecraft:log:0", "minecraft:wool:14", "minecraft:iron_ingot:0", "minecraft:chest:0", "minecraft:potion:8193",
             "gregtech:gt.metaitem.01:2032", "gregtech:gt.metaitem.01:3032", "gregtech:gt.metaitem.01:4032",
             "gregtech:gt.metaitem.01:6032", "gregtech:gt.metaitem.01:11032"]
    picks = [by_id[i] for i in named if i in by_id and by_id[i].get("image")]
    random.seed(1)
    picks += random.sample(with_image, min(150, len(with_image)))
    fpicks = [f for f in fluids if f.get("image")][:40]
    cells = [(p["image"], p["id"]) for p in picks] + [(f["image"], "fluid:" + f["id"]) for f in fpicks]
    cols, size = 12, 64
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * size, rows * (size + 4)), (40, 40, 48, 255))
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
