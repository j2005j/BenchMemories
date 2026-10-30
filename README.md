# BenchMemories

**JBenchmark 1.1.1** (Kishonti LP, 2005) running natively on Sony
PlayMemories cameras' hidden Android subsystem — built for the
**A6300** (Android 4.1.2 / API 16, Sony CE Linux 3.0.27 RT kernel).

The benchmark JAR is baked into the APK: the app icon *is* the
benchmark. No file picker (the camera has none), no settings screens,
no emulator UI — one tap, five tests, results.

## How it works

Android ships zero `javax.microedition.*` APIs, so J2ME code can't just
run. Forking a full emulator (e.g. j2me-loader) drags in AndroidX,
databinding, rxjava, a settings DB and NDK code — and on this camera
that stack white-screens. Instead this project uses three small layers:

| Layer | What it is |
|---|---|
| **Original benchmark** | All 4 classes from the shipped jar (`JBenchmark/JBenchmark`, `a`, `b`, `c`), **dexed unmodified at build time** by `d8` into the app's `classes.dex`. Scores come from authentic 2005 bytecode. |
| **Mini-MIDP shim** | 18 hand-written `javax.microedition.*` classes covering exactly the ~40 MIDP calls the benchmark's bytecode makes (extracted via `javap`), implementing MIDP-1.0 semantics (anchor math, pie `fillArc`, font sizing, `callSerially` pump). |
| **Host** | `LauncherActivity` instantiates the MIDlet directly; `Host` owns a 240×320 back buffer and maps `Display.callSerially` onto a UI `Handler`; `EmuView` presents the buffer nearest-neighbor scaled. |

## Baked settings

These are fixed in code — no configuration UI:

| Setting | Value | How |
|---|---|---|
| Shader | Fast (no filter) | all paints: no anti-alias, no bitmap filtering, no dither; nearest-neighbor present pass |
| Limit FPS | 99999999 (uncapped) | `repaint()` paints synchronously — one paint per loop iteration, no vsync frame-dropping |
| Anti-Aliasing | OFF | never enabled anywhere |
| Resolution of emulation | 240×320 | the logical back buffer; `getWidth()/getHeight()` report exactly that |
| Immediate processing | inherent | frames are drawn straight into the buffer immediately |
| Graphics mode | software (no GL) | `hardwareAccelerated=false` — GL is the prime suspect for camera hangs/white screens; software rendering cannot hit that failure mode |

## Controls (camera has no touch screen)

| Key | Action |
|---|---|
| MENU | exit the app |
| DPAD UP / DOWN | select command on the soft bar |
| DPAD LEFT / RIGHT | scroll list content |
| CENTER / OK | run selected command |
| BACK (if present) | fires the MIDP "Back" command |

A red `key:<code>` badge appears for 3 s after each press — if a button
does nothing, its Android keycode is shown and can be mapped in
`Host.handleKey()`.

## The benchmark JAR is not included

`JBenchmark.jar` is (c) 2005 Kishonti LP and is **not redistributed in
this repository**. `build.sh` downloads it on first build from the
public Simbians' Attic mirror:

```
https://files.simbiansattic.lt/downloads/JBtools/JB/JBenchmark.jar
sha256 d96f92052f68afe09f48841d4c232fe1396d64da01445bf44653b9440a583f03
md5    a78b0a813f53b31dac36a038b9b35bc9
```

If the mirror disappears, any copy of JBenchmark 1.1.1 with that
checksum works — drop it at `app/src/main/assets/JBenchmark.jar`. The
compiled APK releases below necessarily embed the dexed classes and
`images/earth.png`; JBenchmark was freeware, and this project is
unaffiliated with Kishonti LP.

## Build

Requires JDK 17+ and an Android SDK with `platforms;android-34` and
`build-tools;34.0.0`:

```sh
export ANDROID_HOME=/opt/android-sdk
./build.sh        # fetches the jar on first run
# → build/jbenchmark.apk
```

`build.sh` pipeline: `aapt2` → `javac` (against the jar) → `d8`
(our classes + the jar's classes → one dex) → zipalign → apksigner.

## Install

Sideload `build/jbenchmark.apk` with your usual OpenMemories method.
Expect ~50 s of 100 % CPU for the 5 × 10 s tests, then the results form.

## Camera-safety design notes

- The benchmark's run loop re-queues itself forever by design; the host
  detects the finished state (test index ≥ 5) and throttles that loop to
  10 Hz so the camera's CPU isn't pegged while results are on screen.
  Restart immediately restores full speed.
- Window background is hard black and every failure path (any thread,
  any exception) renders as on-screen error text — no white screens.
- Zero permissions; the optional "Upload results" step fails gracefully
  offline (`Connector.open` throws by design).
- Form mutations are synchronized — the upload runner appends to the
  results form from a background thread.

## Version history

- **v1.0** — first release: original jar classes dexed unmodified,
  18-class MIDP-1.0/CLDC shim, software renderer, baked settings.

## Credits

- **JBenchmark 1.1.1** — Kishonti LP (2005); preserved by the
  Simbians' Attic archive.
- **[j2me-loader](https://github.com/nikita36078/j2me-loader)**
  (Apache-2.0) — MIDP semantics (anchor constants, `fillArc` angle
  negation, font size mapping) verified against its implementation.
