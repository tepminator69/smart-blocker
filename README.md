# Smart Blocker

An Android app that **blocks apps at set times**, enforces a **whole-day limit** and **per-app limits**, and sends **reminders**. It uses a glass design in black, white and grey.

> Honest status: the code was written without a phone to test on. The first build may need a fix or two. Report anything strange and it gets fixed.

---

## Install it on your phone

1. Open the repository's **Releases** page on your phone and download **SmartBlocker.apk**.
2. Open the file. Android warns that it's from an unknown source. Allow installing from your browser or file manager (Android asks once).
3. Open **Smart Blocker**, tap the **shield** button (top right) and do the 4 setup steps:

| Step | Why it's needed |
|---|---|
| **Accessibility** | Tells the app which app just opened, so it can pause it. It never reads your screen. |
| **Usage access** | Counts your minutes in each app (the same data Digital Wellbeing uses). |
| **Notifications** | For reminders. |
| **Battery** | Stops Android from closing Smart Blocker to save power. |

**Is the Accessibility switch greyed out?** Android 13 and newer does this for apps that aren't from the Play Store. Open **App info → ⋮ (top right) → Allow restricted settings**, then switch it on again.

### Tecno phones (HiOS)
Tecno closes background apps by itself. Also do this (names can differ a little by model):
- **Phone Master → Toolbox → Auto-start management** → allow Smart Blocker.
- **Settings → Battery Lab → Battery saving settings** → switch off power saving management for Smart Blocker.

---

## How it works

```
You open TikTok
   │
   ▼
BlockerService (Accessibility) hears "TikTok window opened"
   │
   ▼
Engine checks the rules ──► block running?  per-app limit used?  whole-day limit used?
   │                                   │
   no → nothing happens                yes → go to Home + show BlockActivity (block.html)
```

Every 20 seconds the service checks again (a limit can run out while you're scrolling) and sends reminders.

## The files and what they do

| File | What it does |
|---|---|
| `app/src/main/AndroidManifest.xml` | The app's ID card: name, screens, permissions, the Accessibility service. |
| `BlockerService.kt` | **The heart.** Hears every app opening and closes the ones that are blocked. Runs the 20-second check. |
| `Engine.kt` | **The rules.** Decides if an app must be paused now, and why. |
| `Usage.kt` | Counts minutes per app today from Android's usage records. |
| `Reminders.kt` | Decides which reminder to send (50%, 80%, 5 min left, block starts soon, evening summary). |
| `Notifier.kt` | Actually shows notifications. |
| `Store.kt` | Saves your rules as JSON text. Also checks Strict mode ("is the new rule weaker?"). |
| `DayState.kt` | Things that reset at midnight: snoozes used and reminders already sent. |
| `Time.kt` | Clock maths: "is 22:30–07:00 active right now?" |
| `Apps.kt` | Installed apps, their names, and which ones not to count (home screen, keyboard). |
| `MainActivity.kt` | Shows `index.html` and lets JavaScript call Kotlin (the **Bridge**). |
| `BlockActivity.kt` | Shows `block.html`, the "app is paused" screen. |
| `Web.kt` | Shared WebView setup, edge-to-edge drawing. |
| `assets/www/index.html, app.css, app.js` | **The whole look of the app.** Plain HTML/CSS/JS, so you can restyle it yourself. |
| `assets/www/block.html` | The block screen with the countdown and the 10-second breathing pause. |
| `.github/workflows/build.yml` | Tells GitHub how to build the APK and put it on the Releases page. |

### Edit the design on your PC
Open `app/src/main/assets/www/index.html` (or `block.html`) in Chrome. Outside the app it shows pretend data, so you can change `app.css` and refresh. The colours and fonts are at the top of `app.css` (`:root { --bg: … }`).

## New words

| Word | Meaning |
|---|---|
| **APK** | The install file of an Android app (like `.exe` on Windows). |
| **Accessibility service** | A helper app that Android tells about what happens on screen. Made for screen readers; blockers use it to see which app opened. |
| **Kotlin** | The main language for Android apps. |
| **WebView** | A browser window inside an app. Our screens are web pages shown in one. |
| **Bridge** (`@JavascriptInterface`) | Kotlin functions that JavaScript is allowed to call, e.g. `Android.getStatus()`. |
| **SharedPreferences** | A small file where an app saves settings. |
| **Gradle** | The tool that builds the code into an APK. |
| **GitHub Actions** | GitHub's computers that run the build for us. |
| **Sideloading** | Installing an APK that isn't from the Play Store. |

## Limits (honest)
- You can always uninstall it. It helps your self-control, but it isn't a lock.
- Minutes come from Android's usage records, so they can differ a little from Digital Wellbeing.
- Snoozes: 3 per day, 5 minutes each, after a 10-second pause. None in Strict mode or in Strict blocks.
