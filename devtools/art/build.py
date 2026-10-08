"""The Scout, as code: its model (a one-man submarine, a block a metre), its two profiles, its
recipes, its lang and its sounds' entries.

Run from the repository root:

    uv run --no-project --with numpy python devtools/art/build.py

Everything it writes is committed; this script is the source of truth for those files. nfx's words
are the brief ("a one-man wet-bike sub, bubble canopy, single prop, one spotlight", fast, cheap and
fragile); the model is our own. Copyright 2026 Rusty Shackleford and nfx.
SPDX-License-Identifier: AGPL-3.0-or-later.

The pod and its canopy are a voxel shell (the shared minecraft mods/tools/bbgen/voxel.py): a
superellipse section swept along a rounded nose, a body and a tapering tail, with an ellipsoid dome
over the pilot; hollow through the cabin, the dome glazed wherever it stands above the pod, so the
pilot sits low in the pod with his head in the bubble. The spotlight is the nose itself; the screw
turns in a duct the tail's four fins carry; the locker's chest is hidden in the solid tail under a
hatch.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT.parent / "tools/bbgen"))
import bbgen  # noqa: E402  (the shared Blockbench writer: minecraft mods/tools/bbgen)
import voxel  # noqa: E402

NS = "scout_sub"
NAME = "scout"
ASSETS = ROOT / "src/main/resources/assets" / NS
DATA = ROOT / "src/main/resources/data" / NS

PX = 16
ORIGIN = 26 / PX            # the model's origin: 1.625 m aft of the nose, on the keel line

# ------------------------------------------------------------------ the pod

LENGTH = 3.00               # the nose to the tail cone's end
CENTRE = 0.72               # the pod's axis, metres up
W0, H0 = 0.70, 0.48         # its half-beam and half-height amidships
N = 2.4                     # the section's superellipse
BOW, STERN = 0.80, 1.85     # where the nose's curve ends and the tail's taper begins
CABIN = (0.0, 2.05)         # the stations the inside is hollow between
FLOOR_Y = 0.40
CELL = (1, 1, 1)            # the pod's lattice, voxels: a pixel every way (it is small)
DOME = ((0.0, 1.08, 1.10), (0.52, 0.72, 0.78))     # the canopy's middle (x, y, s) and its radii


def section(s):
    """The pod's half-beam and half-height at stations s (an array), metres."""
    s = np.asarray(s, dtype=float)
    f = np.zeros_like(s)
    bow = (s >= 0) & (s < BOW)
    f[bow] = (1 - ((BOW - s[bow]) / BOW) ** 2.4) ** (1 / 2.4)
    f[(s >= BOW) & (s <= STERN)] = 1.0
    st = (s > STERN) & (s <= LENGTH)
    f[st] = 1 - 0.68 * ((s[st] - STERN) / (LENGTH - STERN)) ** 1.4
    return W0 * f, H0 * f


def in_pod(X, Y, S):
    w, h = section(S)
    w, h = np.maximum(w, 1e-9), np.maximum(h, 1e-9)
    return (np.abs(X / w) ** N + np.abs((Y - CENTRE) / h) ** N <= 1.0) & (w > 1e-6)


def in_dome(X, Y, S):
    (cx, cy, cs), (rx, ry, rs) = DOME
    return (((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2 + ((S - cs) / rs) ** 2 <= 1.0) & (Y >= cy - 0.3)


def pod(v: voxel.Voxels) -> None:
    """The shell: the pod and the dome together, hollowed through the cabin; glass where the dome stands
    clear of the pod, a dark rim where they meet, the floor across the cabin's bottom."""
    X, Y, S = v.lattice(CELL)
    p = np.broadcast_to(in_pod(X, Y, S), np.broadcast_shapes(X.shape, Y.shape, S.shape))
    d = np.broadcast_to(in_dome(X, Y, S), p.shape)
    solid = p | d
    hollow = voxel.erode(solid) & (S >= CABIN[0]) & (S <= CABIN[1])
    shell = solid & ~hollow
    glass = shell & ~p
    rim = shell & p & voxel.dilate(glass)           # the pod's voxels touching the glass
    v.add("paint/pod", "paint", v.up(shell & p & ~rim, CELL))
    v.add("trim/canopy_rim", "trim", v.up(rim, CELL))
    v.add("glass/canopy", "glass", v.up(glass, CELL))
    v.add("interior/floor", "floor", v.up(hollow & (Y < FLOOR_Y), CELL))
    v.envelope |= v.up(solid, CELL)


# ------------------------------------------------------------------ the fittings

LAMP = (0.0, CENTRE, 0.0)                         # the spotlight's face, the nose's tip
TUBE = (0.0, 0.19, 0.28)                          # the torpedo tube's muzzle, under the chin
SCREW = (0.0, CENTRE, 3.13)                       # the screw's hub
LOCKER = (0.55, 2.38, 0.40)                       # the locker chest's floor, its middle station and its scale
HATCH_TOP = (0.0, 1.95, 1.10)                     # where the pilot getting out comes up: over the dome


def nose(v: voxel.Voxels) -> None:
    """The spotlight: the nose's foremost voxels within a lamp's radius, lit, in a dark ring."""
    X, Y, S = v.coords()
    r2 = X ** 2 + (Y - CENTRE) ** 2
    paint = v.parts["paint/pod"][1]
    front = np.broadcast_to(S < 0.30, paint.shape)
    v.move("paint/pod", "lamps/spot", "lamp", front & (r2 <= 0.14 ** 2))
    v.move("paint/pod", "trim/spot_ring", "trim", front & (r2 > 0.14 ** 2) & (r2 <= 0.19 ** 2))


def tube(v: voxel.Voxels) -> None:
    """The torpedo tube under the keel, between the skids."""
    x, y, s = TUBE
    v.box("metal/tube", "metal", x - 0.09, x + 0.09, y - 0.09, y + 0.09, s + 0.02, 1.40)
    v.box("trim/muzzle", "trim", x - 0.06, x + 0.06, y - 0.06, y + 0.06, s, s + 0.02)
    v.box("metal/tube", "metal", x - 0.05, x + 0.05, y + 0.09, 0.34, 0.90, 1.10)       # its strut


def hatch(v: voxel.Voxels) -> None:
    """The locker's hatch on the tail, a voxel proud, and its handle."""
    xs, ss = v.xs, v.ss
    plate = (np.abs(xs[:, None]) <= 0.22) & (ss[None, :] >= 2.12) & (ss[None, :] <= 2.62)
    v.add("hatch/locker", "hatch", voxel.proud(v.solid(), plate, axis=1, side=1))
    handle = (np.abs(xs[:, None]) <= 0.07) & (ss[None, :] >= 2.52) & (ss[None, :] <= 2.56)
    v.add("trim/locker_handle", "trim", voxel.proud(v.solid(), handle, axis=1, side=1))


def tail(v: voxel.Voxels) -> list[dict]:
    """Four fins out to a duct round the screw; returns the propeller's entry."""
    X, Y, S = v.coords()
    cx, cy, cs = SCREW
    r2 = (X - cx) ** 2 + (Y - cy) ** 2
    duct = (r2 > 0.31 ** 2) & (r2 <= 0.37 ** 2) & (S >= 2.98) & (S <= 3.26)
    v.add("paint/duct", "paint", duct)
    # The fins: from the cone out to the duct, their leading edges swept back from 2.45.
    reach = 0.34 * np.clip((S - 2.45) / 0.45, 0, 1)
    thin_x, thin_y = np.abs(X) <= 0.03, np.abs(Y - cy) <= 0.03
    along = (S >= 2.45) & (S <= 3.04)               # ahead of the blades
    v.add("paint/fins", "paint", thin_x & (np.abs(Y - cy) <= np.maximum(reach, 0.0) + 0.02) & along)
    v.add("paint/fins", "paint", thin_y & (np.abs(X) <= np.maximum(reach, 0.0) + 0.02) & along)
    v.box("metal/shaft", "metal", -0.04, 0.04, cy - 0.04, cy + 0.04, LENGTH, 3.16)
    blades = "propeller/blades"
    v.box(blades, "prop", -0.06, 0.06, cy - 0.06, cy + 0.06, 3.06, 3.20, moving=True)
    v.box(blades, "prop", -0.29, 0.29, cy - 0.03, cy + 0.03, 3.10, 3.15, moving=True)
    v.box(blades, "prop", -0.03, 0.03, cy - 0.29, cy + 0.29, 3.11, 3.16, moving=True)
    return [{"part": {"group": "propeller"}, "pivot": mesh(*SCREW), "axis": [0, 0, 1], "speed": 1.0}]


def skids(v: voxel.Voxels) -> None:
    v.mirror("trim/skids", "trim", 0.36, 0.44, 0.0, 0.09, 0.50, 2.40)
    for s in (0.70, 1.55, 2.25):
        v.mirror("trim/skids", "trim", 0.37, 0.43, 0.09, 0.40, s, s + 0.07)


# The pilot sits low in the pod, the head in the dome: hips, and the eye a seated rider's 1.0 over them.
SEAT = {"at": (0.0, 0.50, 1.25), "eye": (0.0, 1.52, 1.15)}


def interior(v: voxel.Voxels) -> None:
    """The seat, the stick, a small panel at the dome's foot."""
    v.box("interior/seat", "seat", -0.20, 0.20, FLOOR_Y, 0.50, 1.10, 1.45, inside=True)
    v.box("interior/seat", "seat", -0.20, 0.20, 0.50, 0.95, 1.45, 1.53, inside=True)
    v.box("interior/stick", "metal", -0.04, 0.04, FLOOR_Y, 0.78, 0.83, 0.89, inside=True)
    v.box("interior/stick", "trim", -0.05, 0.05, 0.78, 0.86, 0.82, 0.90, inside=True)
    v.box("interior/panel", "panel", -0.18, 0.18, 0.94, 1.04, 0.56, 0.62, inside=True)


def mesh(x: float, y: float, s: float) -> list[float]:
    """A point in metres as a profile writes it: mesh pixels, +Z the nose."""
    return [round(x * PX, 3), round(y * PX, 3), round((ORIGIN - s) * PX, 3)]


def make_atlas() -> bbgen.TexelAtlas:
    a = bbgen.TexelAtlas(size=256, density=1.0, seed=0x5C0)
    a.material("paint", (232, 232, 226), w=128, h=64, grain=9)
    a.material("glass", (170, 210, 222), w=64, h=32, grain=4, alpha=96)
    a.material("trim", (54, 58, 64), w=32, h=32, grain=8)
    a.material("metal", (146, 150, 156), w=32, h=32, grain=10)
    a.material("prop", (186, 148, 74), w=32, h=32, grain=8)
    a.material("lamp", (252, 248, 226), w=16, h=16, grain=3)
    a.material("hatch", (110, 116, 124), w=32, h=32, grain=10,
               pattern=lambda x, y, c: bbgen.shade(c, -24) if x % 6 == 0 or y % 6 == 0 else c)
    a.material("floor", (70, 70, 66), w=32, h=32, grain=10)
    a.material("seat", (36, 38, 42), w=32, h=32, grain=8)

    def dials(x, y, c):
        dx, dy = x % 5 - 2, y % 5 - 2
        if max(abs(dx), abs(dy)) == 2:
            return c
        if max(abs(dx), abs(dy)) == 1:
            return (150, 152, 150) if (dx, dy) != (1, -1) else (226, 226, 214)
        return (12, 12, 14)

    a.material("panel", (30, 32, 34), w=32, h=16, grain=4, pattern=dials, scale=2.0)
    return a


def build_model() -> tuple[bbgen.Model, list[dict]]:
    v = voxel.Voxels(half_width=0.80, height=2.0, length=3.65, origin=ORIGIN, start=-0.25)
    pod(v)
    nose(v)
    tube(v)
    hatch(v)
    props = tail(v)
    skids(v)
    interior(v)
    m = bbgen.Model(NAME, make_atlas(), seed=f"{NS}/{NAME}")
    print(f"{NAME}: {v.write(m)} boxes")
    return m, props


# ---------------------------------------------------------------- the profiles

def vehicle_profile() -> dict:
    """The Scout to Vanilla Wheels: its look, its seat, what it rests on beached, its tank, its
    locker, its spotlight, its paint and glass, its camera and its loop."""
    return {
        "mesh": f"{NS}:{NAME}",
        "scale": 0.0625,
        "handedness": "right",
        # The body's own box stands amidships, the dome in it; the nose and the tail have boxes of their
        # own, the tail's high enough to take a click on the locker's hatch.
        "body": {"width": 1.4, "length": 3.3, "height": 1.8,
                 "parts": [{"at": mesh(0, 0.20, 0.45), "width": 0.9, "height": 1.3},
                           {"at": mesh(0, 0.25, 2.80), "width": 0.95, "height": 1.0}]},
        "seats": [{"at": mesh(*SEAT["at"]), "eye": mesh(*SEAT["eye"]), "driver": True}],
        # The skids' ends: where it rests, beached.
        "wheels": {"radius": 1, "drawn": False,
                   "positions": [{"forward": mesh(0, 0, s)[2], "right": round(side * 0.40 * PX, 3)}
                                 for s in (0.60, 2.30) for side in (-1, 1)]},
        "engine": {"max_speed": 0.44, "acceleration": 0.02, "reverse_speed": 0.15, "brake": 0.04},
        "climb": 0.5,
        "mass": 1.5,
        "fuel": {"capacity": 24000},
        # The locker: a chest's single row, hidden in the tail under its hatch.
        "storage": {"chests": [{"at": mesh(0, LOCKER[0], LOCKER[1]), "yaw": 90, "scale": LOCKER[2], "rows": 1}]},
        "headlights": {"at": [mesh(*LAMP)], "part": {"group": "lamps"}, "range": 16},
        "paint": {"part": {"group": "paint"}, "default": "yellow", "factory": "#f2c230"},
        "glass": {"group": "glass"},
        "cockpit": {"group": "glass"},   # the dome: glass to everyone else, clear to the pilot looking out through it (Vanilla Wheels D-0031)
        "sounds": {"engine": f"{NS}:engine", "pitch": [0.9, 1.45], "volume": [0.25, 0.7]},
        "camera": 7.0,
        "repair": {"ingredient": {"tag": "c:ingots/copper"}, "full_cost": 16},
    }


#       x0     x1     y0    y1    s0    s1
HULL = [(-0.62, 0.62, 0.25, 1.15, 0.00, 0.45),     # the nose
        (-0.70, 0.70, 0.00, 1.20, 0.45, 1.85),     # the body on its skids, the tube under it
        (-0.52, 0.52, 1.20, 1.80, 0.40, 1.85),     # the dome
        (-0.42, 0.42, 0.30, 1.14, 1.85, 3.26)]     # the tail, its fins and the duct


def submarine_profile(props: list[dict]) -> dict:
    """The Scout to Submersibles: fast, nimble and fragile (nfx's numbers, in this protocol's terms)."""
    return {
        # Top speed 0.030 * (1 - 0.064) / 0.064 = 0.44 blocks a tick, 8.8 a second.
        "properties": {"engineSpeed": 0.030, "friction": 0.020, "verticalSpeed": 0.14, "yawSpeed": 3.5,
                       "acceleration": 1.0, "fuel": 0.8, "durability": 1.5, "stabilizer": 0.05, "wind": 1.0},
        "hull_drag": 0.044,
        "spool_ticks": 20,
        "rise": 0.06,
        "draft": 0.45,              # surfaced, the upper pod and the dome out
        "tilt": 14,
        "hull": [{"from": mesh(x0, y0, s1), "to": mesh(x1, y1, s0)} for x0, x1, y0, y1, s0, s1 in HULL],
        "propellers": props,
        "upgrades": 2,
        "weapons": [mesh(*TUBE)],
        "hatch": mesh(*HATCH_TOP),
    }


# ---------------------------------------------------------------- the recipes

CHASSIS = {"vanillawheels:vehicle": f"{NS}:{NAME}"}

# The pod, at a crafting table: copper (cheap, as nfx's brief has it) under a bubble, round its locker.
CHASSIS_RECIPE = {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": ["GGG", "CBC", " C "],
    "key": {"G": {"tag": "c:glass_blocks"}, "B": {"item": "minecraft:barrel"}, "C": {"tag": "c:storage_blocks/copper"}},
    "result": {"id": "vanillawheels:chassis", "count": 1, "components": CHASSIS},
}

# And the submarine itself, packed, from the pod and an engine. Only the Scout's own chassis will do.
SUB_RECIPE = {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [{"type": "neoforge:components", "items": "vanillawheels:chassis", "components": CHASSIS},
                    {"item": "vanillawheels:engine"}],
    "result": {"id": "vanillawheels:vehicle", "count": 1, "components": CHASSIS},
}


def unlock(recipe: str, item: str) -> dict:
    """The recipe-book unlock: on holding what starts it, so a pooled crafting fill finds it."""
    return {"parent": "minecraft:recipes/root",
            "criteria": {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe}},
                         "has_it": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}},
            "requirements": [["has_the_recipe", "has_it"]],
            "rewards": {"recipes": [recipe]}}


SOUNDS_JSON = {
    "engine": {"sounds": [{"name": f"{NS}:engine", "attenuation_distance": 24}]},
}

LANG = {
    f"vehicle.{NS}.{NAME}": "Scout",
}


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    m, props = build_model()
    m.write(ASSETS / "vanillawheels/mesh" / f"{NAME}.bbmodel")
    write_json(DATA / "vanillawheels/vehicle" / f"{NAME}.json", vehicle_profile())
    write_json(DATA / "submersibles/submarine" / f"{NAME}.json", submarine_profile(props))
    write_json(DATA / "recipe" / f"{NAME}_chassis.json", CHASSIS_RECIPE)
    write_json(DATA / "recipe" / f"{NAME}.json", SUB_RECIPE)
    write_json(DATA / "advancement/recipes" / f"{NAME}_chassis.json", unlock(f"{NS}:{NAME}_chassis", "#c:storage_blocks/copper"))
    write_json(DATA / "advancement/recipes" / f"{NAME}.json", unlock(f"{NS}:{NAME}", "vanillawheels:chassis"))
    write_json(ASSETS / "lang/en_us.json", LANG)
    if (ASSETS / "sounds/engine.ogg").exists():
        write_json(ASSETS / "sounds.json", SOUNDS_JSON)


SOUND_SRC = ROOT / "devtools/art/sounds/src"


def sounds() -> None:
    """The engine's loop, from the submarine engine's steady whirr: see devtools/art/sounds/SOURCES.md. Needs ffmpeg and numpy (`--with numpy`)."""
    sys.path.insert(0, str(ROOT.parent / "tools/sound"))
    import cutlib  # noqa: E402  (the shared cutter: minecraft mods/tools/sound)
    start, length, fade = 35.52, 3.0656, 0.20
    cutlib.write_ogg(ASSETS / "sounds/engine.ogg",
                     cutlib.loop(SOUND_SRC / "438731-submarine-engine-start.ogg", start, start + length + fade, fade, 0.8))


if __name__ == "__main__":
    if sys.argv[1:] == ["sounds"]:
        sounds()
    else:
        main()
