#!/usr/bin/env python3
"""
SecureCC asset validation — catches the class of mistakes that shipped
in the 1.4.8 secure networking build (2026-09-24):

  1. Modem used parent "block/block" with no "elements" -> invisible hole
     in-world (blockstate OK, model silently renders nothing).
  2. SECURE_CABLE / SECURE_MODEM ItemBlocks were never registered in
     ClientRegistration -> missing inventory/held models.
  3. Cable had no connection logic / multipart blockstate -> placed
     cables never visually joined.

Run:  python3 tools/validate_assets.py [--strict]
Exit 0 = all checks pass. Exit 1 = problems found (listed).
Intended to run before every build (build.sh calls it).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/maximarcana/securecc"
ASSETS = ROOT / "src/main/resources/assets/securecc"

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def load_json(path):
    try:
        return json.loads(path.read_text())
    except Exception as e:
        err(f"{path.relative_to(ROOT)}: invalid JSON: {e}")
        return None


# ---------- 1. Discover registered blocks/items ----------

modblocks = (SRC / "ModBlocks.java").read_text()
# e.g. public static final Block SECURE_CABLE = new BlockSecureCable();
block_fields = re.findall(r"public static final Block (\w+)\s*=", modblocks)
# Map field -> registry name via setRegistryName in the block class is
# fragile; instead derive from the block class constructor call.
# Fallback: field name lowercased.
field_to_registry = {}
for field in block_fields:
    # find the block class, then its setRegistryName call
    m = re.search(rf"{field}\s*=\s*new (\w+)\(", modblocks)
    if m:
        cls = m.group(1)
        cls_file = SRC / "block" / f"{cls}.java"
        if cls_file.exists():
            txt = cls_file.read_text()
            rm = re.search(r'setRegistryName\(SecureCC\.MODID,\s*"([^"]+)"\)', txt)
            if rm:
                field_to_registry[field] = rm.group(1)
                continue
    # fallback guess
    field_to_registry[field] = field.lower().replace("secure_", "secure_")

# Items: public static final Item SECURE_CABLE = ...
moditems = (SRC / "ModItems.java").read_text()
item_fields = re.findall(r"public static final Item (\w+)\s*=", moditems)

# Map item field -> registry name, via the item class's setRegistryName
# (ItemBlock subclasses take the block; standalone items set their own).
item_to_registry = {}
for f in item_fields:
    m = re.search(rf"{f}\s*=\s*new (\w+)\(", moditems)
    if not m:
        continue
    cls = m.group(1)
    # ItemBlock wrappers: registry name comes from the block
    if cls.startswith("ItemSecure") or cls.startswith("Item"):
        # search block/ then item/ dirs
        for sub in ("block", "item"):
            cls_file = SRC / sub / f"{cls}.java"
            if cls_file.exists():
                txt = cls_file.read_text()
                rm = re.search(r'setRegistryName\(SecureCC\.MODID,\s*"([^"]+)"\)', txt)
                if rm:
                    item_to_registry[f] = rm.group(1)
                    break
                # constant-based: REGISTRY_NAME = "foo"; setRegistryName(MODID, REGISTRY_NAME)
                cm = re.search(r'REGISTRY_NAME\s*=\s*"([^"]+)"', txt)
                if cm and "setRegistryName" in txt:
                    item_to_registry[f] = cm.group(1)
                    break
                # ItemBlock(Item) constructor pattern: registry name inherited
                # from block — look for getRegistryName passthrough
                if "Block" in cls and "ItemBlock" in txt:
                    # find which ModBlocks field it wraps
                    bm = re.search(r"ModBlocks\.(\w+)", txt)
                    if bm and bm.group(1) in field_to_registry:
                        item_to_registry[f] = field_to_registry[bm.group(1)]
                        break
        if f not in item_to_registry and f in field_to_registry:
            item_to_registry[f] = field_to_registry[f]

client_reg = (SRC / "client/ClientRegistration.java").read_text()
registered_models = set(re.findall(r"registerItemModel\(ModItems\.(\w+)\)", client_reg))

# ---------- 2. Per-block checks ----------

for field, reg in field_to_registry.items():
    # blockstate
    bs = ASSETS / "blockstates" / f"{reg}.json"
    if not bs.exists():
        err(f"blockstates/{reg}.json MISSING (block field {field})")
        continue
    bs_data = load_json(bs)
    if bs_data is None:
        continue

    # collect model references from blockstate
    models_referenced = set()

    def collect_models(obj):
        if isinstance(obj, dict):
            if "model" in obj and isinstance(obj["model"], str):
                models_referenced.add(obj["model"])
            for v in obj.values():
                collect_models(v)
        elif isinstance(obj, list):
            for v in obj:
                collect_models(v)

    collect_models(bs_data)

    is_multipart = "multipart" in bs_data

    # --- block models ---
    # For multipart, every referenced model must exist as models/block/<name>.json
    # For variants, the single model must exist.
    for ref in models_referenced:
        # ref like "securecc:secure_cable_center" or "securecc:block/secure_modem"
        name = ref.split(":", 1)[-1]
        if name.startswith("block/"):
            name = name[len("block/"):]
        model_path = ASSETS / "models/block" / f"{name}.json"
        if not model_path.exists():
            err(f"blockstates/{reg}.json references missing model: models/block/{name}.json")
            continue
        data = load_json(model_path)
        if data is None:
            continue
        # THE MODEM-HOLE CHECK: parent block/block with no elements renders nothing.
        parent = data.get("parent", "")
        has_elements = "elements" in data
        if parent == "block/block" and not has_elements:
            err(
                f"models/block/{name}.json: parent \"block/block\" with no \"elements\" "
                f"renders as NOTHING (invisible hole in-world). "
                f"Use \"block/cube\" / \"block/cube_all\" or add elements. "
                f"[This exact bug shipped the 1.4.8 modem hole.]"
            )
        # multipart part models should also not be empty
        if is_multipart and parent == "block/block" and not has_elements:
            err(f"models/block/{name}.json (multipart part): empty block/block parent renders nothing.")

    # Non-multipart cable-style blocks: warn if a cable-like block has no
    # connection properties (visual arms will never appear).
    if "cable" in reg:
        block_cls_m = re.search(rf"{field}\s*=\s*new (\w+)\(", modblocks)
        if block_cls_m:
            cls_file = SRC / "block" / f"{block_cls_m.group(1)}.java"
            if cls_file.exists():
                txt = cls_file.read_text()
                if "PropertyBool" not in txt:
                    warn(
                        f"{cls_file.name}: cable block has no PropertyBool connection "
                        f"properties — placed cables will never visually join. "
                        f"[This shipped in 1.4.8: cables were isolated cubes.]"
                    )
                if "IPeripheralProvider" in txt:
                    warn(
                        f"{cls_file.name}: cable implements IPeripheralProvider. "
                        f"Design says cable is pure wire; modem is the peripheral. "
                        f"[Spec drift shipped in 1.4.8.]"
                    )

    # --- item model ---
    item_model = ASSETS / "models/item" / f"{reg}.json"
    if not item_model.exists():
        err(f"models/item/{reg}.json MISSING (block field {field})")
    else:
        data = load_json(item_model)
        if data:
            parent = data.get("parent", "")
            # item model parent should resolve
            if parent.startswith("securecc:"):
                pname = parent.split(":", 1)[1]
                if pname.startswith("block/"):
                    pname = pname[len("block/"):]
                    mp = ASSETS / "models/block" / f"{pname}.json"
                    if not mp.exists():
                        err(f"models/item/{reg}.json parent {parent} -> missing {mp.name}")

    # --- ClientRegistration ---
    # find matching item field (SECURE_CABLE etc.)
    item_field = None
    for f in item_fields:
        if field_to_registry.get(field, "").replace("secure_", "") in f.lower() or f == field:
            item_field = f
            break
    # simpler: same name
    if field in item_fields:
        item_field = field
    if item_field:
        if item_field not in registered_models:
            err(
                f"ClientRegistration.java: registerItemModel(ModItems.{item_field}) MISSING — "
                f"item will be invisible in inventory/hand. "
                f"[This shipped for CABLE and MODEM in 1.4.8.]"
            )
    else:
        warn(f"No ModItems field matching block {field} ({reg}); skipping item-model registration check.")

# ---------- 3. Recipe checks ----------

recipes_dir = ASSETS / "recipes"
known_items = {f"securecc:{r}" for r in field_to_registry.values()}
known_items |= {f"securecc:{r}" for r in item_to_registry.values()}
known_items |= {"minecraft:stone", "minecraft:iron_ingot", "minecraft:redstone",
                "minecraft:ender_pearl", "minecraft:diamond"}
# also allow any minecraft: item
for rp in recipes_dir.glob("*.json"):
    data = load_json(rp)
    if not data:
        continue
    result = data.get("result", {})
    if isinstance(result, dict):
        item = result.get("item", "")
        if item.startswith("securecc:") and item not in known_items:
            err(f"recipes/{rp.name}: result item {item} is not a registered block/item")
        if item.startswith("minecraft:"):
            pass  # vanilla always fine
    # check ingredient items look sane
    key = data.get("key", {})
    for k, v in key.items():
        if isinstance(v, dict) and "item" in v:
            it = v["item"]
            if not (it.startswith("minecraft:") or it.startswith("securecc:") or it.startswith("computercraft:") or it.startswith("plethora:")):
                warn(f"recipes/{rp.name}: unusual ingredient namespace: {it}")

# Every block/item should be produced by at least one recipe (warn, not error —
# some blocks are creative-only or upgrade-only by design).
produced = set()
for rp in recipes_dir.glob("*.json"):
    try:
        data = json.loads(rp.read_text())
    except Exception:
        continue
    result = data.get("result", {})
    if isinstance(result, dict) and "item" in result:
        produced.add(result["item"])

for field, reg in field_to_registry.items():
    if f"securecc:{reg}" not in produced:
        # upgrade-style recipes (secure_upgrade.json etc.) produce these
        warn(f"no recipe produces securecc:{reg} — uncraftable except via upgrade path (intentional?)")

# ---------- 4. Report ----------

print("=" * 60)
print("SecureCC asset validation")
print("=" * 60)
if warnings:
    print(f"\nWARNINGS ({len(warnings)}):")
    for w in warnings:
        print(f"  ⚠ {w}")
if errors:
    print(f"\nERRORS ({len(errors)}):")
    for e in errors:
        print(f"  ✖ {e}")
    print(f"\n{len(errors)} error(s), {len(warnings)} warning(s). Fix errors before building.")
    sys.exit(1)
else:
    print(f"\nAll checks passed. ({len(warnings)} warning(s))")
    print("This run would have caught: modem hole, missing item models.")
    sys.exit(0)
