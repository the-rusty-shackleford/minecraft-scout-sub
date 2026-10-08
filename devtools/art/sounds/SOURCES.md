# Sound sources

The Scout's one sound is cut from a recording taken from freesound.org under the Creative Commons
Zero (CC0 1.0) public-domain dedication, which permits use, modification and redistribution
without attribution. The recordist is credited here anyway, because they deserve it. The file in
`src/` is the recording as downloaded (Freesound's high-quality Vorbis preview); `build.py sounds`
cuts and loops it into `src/main/resources/assets/scout_sub/sounds/` with the shared
`tools/sound/cutlib.py`.

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `438731-submarine-engine-start.ogg` | G45-29-Submarine Engine Start.wav | craigsmith | https://freesound.org/people/craigsmith/sounds/438731/ | CC0 1.0 |

| Shipped sound | Built from |
|---|---|
| `engine.ogg` | 438731, from 35.520 s, 3.066 s long: a submarine's engine run up and steady ("whirring and rumbling ... medium-pitched predominant whirr", from a vintage optical sound library), its tones at 166 to 480 Hz 21 to 27 dB over the floor, from the recording's steadiest stretch after the start (its 100 ms level within 0.4 dB); the start chosen where the 200 ms crossfade's two ends correlate best (0.76) at equal level. Vanilla Wheels plays it from 0.9 to 1.45 of its pitch: a small, busy whirr that climbs as it runs, under the Explorer's deeper hum |

Chosen by measurement over a torpedo's popping propeller (675798, noise with no tone to climb) and
two workshop motors (846905 and 824119, their hiss over 4 kHz).
