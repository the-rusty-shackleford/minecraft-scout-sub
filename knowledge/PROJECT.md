---
title: Scout — project
type: overview
layer: store
tags: [overview]
---

# Scout

## What this is

nfx's one-man submarine (his brief, 2026-10-07, kept in Submersibles' `knowledge/sources/`): data
only, nesting Submersibles (which nests Vanilla Wheels). The plan is
`~/.claude/plans/peppy-scribbling-lollipop.md`, step 3; D-0001 is the model, the seat, the locker,
the numbers and the sound.

## 1.0.1 — released 2026-10-11 in pack 1.82.0 (on Submersibles 1.1.0)

Released on Rusty's go ("Release the 2026-10-10 batch and Survivalist Armor 0.2.0. This is my go."), tag `v1.0.1` at `a0d051a`, the release gate (2026-10-11, `clean build --no-build-cache`) green again on that commit; sha1 `0465628e` on GitHub and on the server (the server repo's `knowledge/releases/pack-1.82.0.md`). Not yet seen in play on the box.


Its durability, 1.5, moved from its submarine profile's `properties` into its Vanilla Wheels profile
(Submersibles 1.1.0; Vanilla Wheels 1.14.0's D-0034), unchanged; it nests Submersibles 1.1.0. Gate:
the release gate (2026-10-10, `clean build --no-build-cache`) green with 8 gametests and the booth's 16 checks.

## Status: 1.0.0 released 2026-10-08 in pack 1.78.0

- Released on Rusty's go with Submersibles 1.0.0: public repo created then, tag `v1.0.0` at
  `9d24a86`; the release gate (2026-10-08) green with 8 GameTests and the booth's 16 checks; sha1
  `afb5f723` on GitHub and on the server (the server repo's `knowledge/releases/pack-1.78.0.md`).
  Rusty passed the photos and drove it in the 4070 playtest before the release. Not yet seen: anyone
  diving it on the box.

The status as built:

- Gate: 8 GameTests and the booth (16 checks) green; no JUnit (no Java beyond the tests). Three
  mutations of the shipped data each failed a test: the recipe taking any pod, the tail's hit box
  removed, the draft changed.
- Needs Vanilla Wheels 1.13.0 with the cockpit's glass (its D-0031), Submersibles 1.0.0 with the
  torpedo's launch sound; both released with it.

## Shape

`devtools/art/build.py` writes every file under `src/main/resources`; edit it, never the outputs.
The model is a voxel shell (`../tools/bbgen/voxel.py`), judged by rendering it with
`../tools/bbgen/render.py` and in the booth.
