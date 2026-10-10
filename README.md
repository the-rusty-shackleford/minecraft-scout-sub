# Scout

A one-man submarine for [Submersibles](https://github.com/the-rusty-shackleford/minecraft-submersibles),
the submarine protocol layered on [Vanilla Wheels](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels),
on NeoForge 1.21.1, from nfx's brief: "a one-man wet-bike sub, bubble canopy, single prop, one
spotlight", fast, cheap and fragile. One block a metre: a pod 3.0 long (3.3 to its duct), 1.4
across, 1.8 to the top of the bubble, yellow. There is no Java in it: the submarine is two datapack
profiles, a Blockbench mesh and a sound, and the protocols do the rest, so diving, the fit-out and
torpedoes are documented in Submersibles' README and the vehicle's keys, fuel, paint and repairs in
Vanilla Wheels'.

**1.0.0** is released (pack 1.78.0).

## What it is

- **One seat**, low in the pod, the pilot's eye up in the bubble. The bubble is glass named in the
  profile's `cockpit` too, so from inside it is not drawn and the pilot sees all round; everyone
  outside sees it (Vanilla Wheels' D-0031).
- **The locker:** a chest's single row, hidden in the solid tail under the hatch behind the bubble.
  A click on the hatch reaches it through the tail's hit box; the inventory key opens it from the
  seat.
- **One spotlight**, the nose itself (Vanilla Wheels' headlamp, range 16), in a dark ring; one
  screw in a duct the tail's four fins carry; a torpedo tube under the chin between the skids.
- **The fit-out:** two upgrade slots and one weapon slot.
- **At the surface** it floats with 0.45 of its height under water: the upper pod and the bubble out.

## Getting one

- **The pod**, at a crafting table: three glass blocks over copper blocks round a barrel
  (`G G G / C B C / _ C _`). Copper, as nfx's brief has it: cheap.
- **The submarine**: the pod and an engine at a crafting table give the packed Scout. Only the
  Scout's own pod matches (`neoforge:components` on the chassis's vehicle). A Mechanic Lift builds
  one too, on land.
- Right-click open water with it to set it down floating.

## Numbers

In Submersibles' terms (`data/scout_sub/submersibles/submarine/scout.json`), from nfx's proposed
stats:

| | |
|---|---|
| top speed | 0.030 thrust, 0.020 slip + 0.044 hull drag: 0.44 blocks a tick, twice the Explorer's |
| climb and dive | 0.14 a tick |
| turn | 3.5 degrees a tick |
| spool | 20 ticks at acceleration 1.0: a second |
| fuel | 24 000 ticks, burnt at 0.8 a tick while driven: 25 minutes |
| durability | 1.5: two thirds of the wear (its Vanilla Wheels profile's `durability` since Submersibles 1.1.0) |
| crashes | Submersibles' defaults: safe below 0.15 a tick, a wreck at 0.8 |
| currents | a swimmer's whole push; stabilizer 0.05; tilts up to 14 degrees |
| repair | copper ingots, 16 for a wreck |

## How it is made

`devtools/art/build.py` writes everything: the model, both profiles, the recipes and their unlocks,
the lang and `sounds.json`. Run from the repository root:

```
uv run --no-project --with numpy python devtools/art/build.py
uv run --no-project --with numpy python devtools/art/build.py sounds    # needs ffmpeg
```

The pod and its bubble are a voxel shell (the shared `minecraft mods/tools/bbgen/voxel.py`): a
superellipse section swept along a rounded nose, a body and a tapering tail, with an ellipsoid dome
over the cabin, on a lattice of single voxels (it is small), hollowed in three dimensions through
the cabin; glass wherever the dome stands clear of the pod, a dark rim where they meet; 531 boxes
with every hidden face left out. The spotlight is the nose's foremost voxels within a lamp's
radius. The folders the profiles select by: `paint` (dyed: the pod, the fins and the duct), `glass`
(translucent, and the `cockpit`), `lamps` (lit with the light), `propeller` (spun by Submersibles).
The engine loop is cut from a CC0 recording of a submarine's engine
(`devtools/art/sounds/SOURCES.md`): a mid-pitched whirr, played from 0.9 to 1.45 of its pitch.

## Verifying it

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew clean build              # gametests and the booth (needs a display; -PskipBooth)
```

Submersibles and Vanilla Wheels come from Maven Local (`./gradlew publishToMavenLocal` in Vanilla
Wheels, then Submersibles). Eight gametests in a tank of water: the profiles; the recipes, and
another vehicle's hull refused; a crafted Scout set down on open water floats at its draft; it holds
its depth and rises to float; it runs up to its own top speed; its pilot breathes at depth and a
second is turned away; the locker opens through the tail's hatch and from the seat; a torpedo leaves
the chin's tube. The booth films it and checks sixteen things a client shows (the pilot's view
through the bubble, the spotlight at night, a real Left Shift diving it, R over the bubble, the
fit-out's slots, the locker).

## License

AGPL-3.0-or-later. Copyright Rusty Shackleford and nfx.
