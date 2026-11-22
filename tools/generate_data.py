#!/usr/bin/env python3
"""Writes the data pack side of Create: Wear & Tear (profiles, recipes, loot). Run from the project root."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
DATA = RES / "data"
ASSETS = RES / "assets/create_wear"

# block: (lifetime in minutes at the reference speed, rated RPM, failure mode)
PROFILES = {
    "shaft": (300, 192, "seize"),
    "cogwheel": (150, 128, "teeth"),
    "large_cogwheel": (180, 96, "teeth"),
    "andesite_encased_shaft": (360, 192, "seize"),
    "brass_encased_shaft": (420, 192, "seize"),
    "metal_girder_encased_shaft": (300, 192, "seize"),
    "andesite_encased_cogwheel": (220, 128, "teeth"),
    "brass_encased_cogwheel": (260, 128, "teeth"),
    "andesite_encased_large_cogwheel": (240, 96, "teeth"),
    "brass_encased_large_cogwheel": (280, 96, "teeth"),
    "gearbox": (240, 160, "seize"),
    "clutch": (240, 192, "seize"),
    "gearshift": (240, 192, "seize"),
    "encased_chain_drive": (200, 160, "teeth"),
    "adjustable_chain_gearshift": (200, 160, "teeth"),
    "crushing_wheel": (200, 128, "smooth"),
    "millstone": (240, 128, "seize"),
    "mechanical_press": (240, 128, "seize"),
    "mechanical_mixer": (240, 128, "seize"),
    "mechanical_saw": (200, 192, "seize"),
    "mechanical_drill": (200, 192, "seize"),
    "encased_fan": (300, 192, "seize"),
    "mechanical_pump": (300, 192, "seize"),
}

# parts that can be hardened in item form with a spout
COATABLE = [
    "shaft", "cogwheel", "large_cogwheel", "gearbox", "clutch", "gearshift", "encased_chain_drive",
    "adjustable_chain_gearshift", "crushing_wheel", "millstone", "mechanical_press", "mechanical_mixer",
    "mechanical_saw", "mechanical_drill", "encased_fan", "mechanical_pump",
]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    ns = DATA / "create_wear"
    write(ns / "data_maps/block/wear_profiles.json", {
        "values": {
            f"create:{block}": {"lifetime": life, "rated_speed": rated, "failure": failure}
            for block, (life, rated, failure) in PROFILES.items()
        }
    })

    write(ns / "recipe/mixing/hardening_compound.json", {
        "type": "create:mixing",
        "ingredients": [
            {"item": "create:andesite_alloy"},
            {"tag": "c:ingots/gold"},
            {"tag": "c:ingots/iron"},
            {"type": "neoforge:single", "amount": 1000, "fluid": "minecraft:water"},
        ],
        "results": [{"id": "create_wear:hardening_compound", "amount": 1000}],
    })

    for part in COATABLE:
        write(ns / f"recipe/filling/hardened_{part}.json", {
            "type": "create:filling",
            "ingredients": [
                # only untouched items: a worn part keeps its wear, it cannot be refreshed this way
                {"type": "neoforge:components", "items": f"create:{part}", "components": {}, "strict": True},
                {"type": "neoforge:single", "amount": 250, "fluid": "create_wear:hardening_compound"},
            ],
            "results": [{"id": f"create:{part}", "components": {"create_wear:reinforced": True}}],
        })

    write(ns / "recipe/crushing/mechanical_scrap.json", {
        "type": "create:crushing",
        "ingredients": [{"item": "create_wear:mechanical_scrap"}],
        "processing_time": 150,
        "results": [
            {"id": "minecraft:iron_nugget", "count": 2},
            {"id": "minecraft:iron_nugget", "chance": 0.5},
            {"id": "create:andesite_alloy", "chance": 0.25},
            {"id": "minecraft:stick", "chance": 0.5},
        ],
    })
    write(ns / "recipe/milling/mechanical_scrap.json", {
        "type": "create:milling",
        "ingredients": [{"item": "create_wear:mechanical_scrap"}],
        "processing_time": 200,
        "results": [
            {"id": "minecraft:iron_nugget", "count": 2},
            {"id": "minecraft:stick", "chance": 0.25},
        ],
    })

    write(ns / "loot_modifiers/wear_data.json", {"type": "create_wear:wear_data", "conditions": []})
    write(DATA / "neoforge/loot_modifiers/global_loot_modifiers.json", {"replace": False, "entries": ["create_wear:wear_data"]})

    write(DATA / "c/tags/fluid/hardening_compound.json", {"values": ["create_wear:hardening_compound", "create_wear:flowing_hardening_compound"]})
    write(DATA / "c/tags/item/buckets/hardening_compound.json", {"values": ["create_wear:hardening_compound_bucket"]})
    write(DATA / "c/tags/item/buckets.json", {"replace": False, "values": ["create_wear:hardening_compound_bucket"]})

    # client resources that are pure boilerplate
    for item in ("hardening_compound_bucket", "mechanical_scrap"):
        write(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"create_wear:item/{item}"}})
    write(ASSETS / "blockstates/hardening_compound.json", {"variants": {"": {"model": "create_wear:block/hardening_compound"}}})
    write(ASSETS / "models/block/hardening_compound.json", {"textures": {"particle": "create_wear:fluid/hardening_compound_still"}})


if __name__ == "__main__":
    main()
