# Clash Tracker (Android)

An overlay app that estimates the opponent's elixir and predicts their card
cycle while Clash Royale runs in the foreground.

## Before you build: things this can't do

- **Elixir count is an estimate, not exact.** The game never displays the
  opponent's elixir anywhere on screen. This app starts at 5, adds regen
  over time, and subtracts detected card costs — it will drift if a play is
  missed or misread. The on-screen "Confidence" value reflects that.
- **Cycle prediction improves as the match goes on.** Until ~5 unique
  opponent cards have been observed, there isn't enough data to guess
  what's "due" to come back around.
- **This is against Clash Royale's Terms of Service.** Supercell prohibits
  third-party tools that read/assist live gameplay. Using this carries real
  account-ban risk, especially in ranked play. This project is provided for
  personal experimentation, not distribution.
- **iOS is not possible.** Apple doesn't allow apps to capture another
  app's screen or draw persistent overlays over other foreground apps.

## What you need to add yourself

1. **Card icon templates** — I can't bundle Supercell's card art (it's
   their IP). Take your own screenshots in a practice match, crop out each
   card icon consistently, and save them as
   `app/src/main/assets/templates/<CardName>.png` (e.g. `Knight.png`,
   `Fireball.png`). Card names must match the `name` field in
   `elixir_costs.json` exactly.
2. **Capture region calibration** — `OverlayService.CAPTURE_REGION` is a
   placeholder guess at where the opponent's "just played" card slot sits
   on screen. Log a few captured frames as bitmaps on your device, find the
   actual pixel rect, and update that constant.
3. **OpenCV Android setup** — this project assumes OpenCV is available via
   Maven Central (`org.opencv:opencv`) as declared in `app/build.gradle`.
   Check Android Studio's dependency resolution when you first sync; if
   that artifact ID has changed, search "OpenCV Android Maven Central" for
   the current one.

## Building with no computer at all (GitHub Actions)

This repo includes `.github/workflows/build-apk.yml`, which makes GitHub's
own cloud servers compile the APK for you — you trigger it and download the
result entirely from your phone's browser. The one thing that still needs
doing once is getting this code onto GitHub in the first place.

1. **Create a GitHub account and a new empty repo** (github.com works fine
   on mobile browsers — Settings icon → "+" → New repository).
2. **Get this code into that repo.** The most reliable phone-only way:
   - **Android:** install [Termux](https://f-droid.org/packages/com.termux/)
     from F-Droid, then inside it:
     ```
     pkg install git -y
     git clone https://github.com/<you>/<repo>.git
     cd <repo>
     # copy/extract this project's files into this folder, then:
     git add .
     git commit -m "initial commit"
     git push
     ```
     (Termux can unzip the project archive too: `pkg install unzip` then
     `unzip ClashTracker.zip`.)
   - **iOS:** a git client like Working Copy can create commits and push
     from your phone, though you'll need to recreate the folder structure
     inside its app since iOS can't run arbitrary shell scripts.
   - If neither is workable, a one-time favor on any computer (library,
     friend's laptop, work PC) to push the initial commit is the fastest
     path — after that, every future change and build happens from your
     phone via the steps below.
3. **Push triggers the build automatically** (or tap "Run workflow" on the
   **Actions** tab of your repo in a mobile browser if you didn't just
   push).
4. Wait ~2–4 minutes, then open the finished run under the **Actions** tab
   and scroll to **Artifacts** — tap `app-debug` to download it as a zip.
5. Extract the zip with your phone's file manager (Android's Files app, or
   iOS Files app) to get `app-debug.apk`.
6. Tap the APK to install (Android will prompt you to allow installs from
   that app once) — this only works on **Android**; iOS can't install
   `.apk` files at all, since that's not its app format.

Every time you update the card templates or tweak `CAPTURE_REGION`, just
edit the file on GitHub's mobile web editor (tap a file → pencil icon) or
push again from Termux, and a fresh APK builds automatically.

## Build steps (with a computer)

1. Install **Android Studio** (this is the more common way to compile an
   APK if you do have access to a computer at some point).
2. Open this folder (`ClashTracker/`) as an existing project:
   **File → Open**, select the `ClashTracker` directory.
3. Let Gradle sync (it will download OpenCV, AndroidX, etc. from Google's
   and Maven Central's repos — this needs your own internet access).
4. Add your card templates and calibrate `CAPTURE_REGION` (see above).
5. Connect your Android phone via USB with Developer Options + USB
   debugging enabled, or use an emulator for initial testing (screen
   capture/overlay behavior won't be meaningful on an emulator since
   there's no real game to capture).
6. **Run ▶** to install and launch on your device, or
   **Build → Build App Bundle(s) / APK(s) → Build APK(s)** to get an
   installable `.apk` file under `app/build/outputs/apk/`.
7. On first launch: tap **"Grant permissions and start overlay"**, allow
   the "draw over other apps" permission, then approve the screen-capture
   prompt. Switch to Clash Royale — the overlay should appear top-left.

## Project structure

```
app/src/main/java/com/example/clashtracker/
  MainActivity.kt      – permission requests, launches the service
  OverlayService.kt    – foreground service: capture loop + overlay window
  CardDetector.kt       – OpenCV template matching against your card icons
  ElixirTracker.kt      – regen timing + cost subtraction
  CycleTracker.kt       – last-4-played tracking and "likely next" guess
  CardDatabase.kt       – loads elixir_costs.json
app/src/main/assets/
  elixir_costs.json     – card name → elixir cost lookup
  templates/             – (you add) cropped card icon PNGs
```

## Reasonable next tweaks

- Add a "double-elixir / overtime detected" trigger based on match-timer
  OCR instead of a fixed 2:00 assumption, in case you want to also handle
  overtime.
- Add manual correction buttons (+1/-1 elixir) in the overlay for when you
  visually catch a missed detection.
- Persist `CAPTURE_REGION` per-resolution in SharedPreferences instead of a
  hardcoded constant, if you test on multiple devices.
