# TTBS-HRMS

An Android app that watches your **TTBS HRMS** punch-out and makes a noise if you forget.

At your deadline (default 6:00 PM) it opens the HRMS dashboard in a hidden browser, reads the
**WORK TIME** card, and if **Punch Out** still shows `00:00:00` while **Punch In** has a real time,
it fires a **full-screen alarm with sound and vibration** — repeating every N minutes (default 2)
until you punch out, snooze, or stop it for the day. Everything is configurable in the app.

---

## How it decides (this is exact, not a guess)

Your dashboard shows:

```
WORK TIME
Sat, 14th of November 2026
Punch In         Punch Out
09:12:33         00:00:00
```

| Punch In | Punch Out | TTBS-HRMS says | What happens |
|---|---|---|---|
| a real time | `00:00:00` | **still punched in** | alarm rings, repeats |
| any | a real time | **punched out** | quiet "all clear", next check tomorrow |
| `00:00:00` | `00:00:00` | **never punched in** | quiet note, no alarm |
| can't read the card | — | **couldn't confirm** | alarm rings but says it couldn't verify |
| login form on screen | — | **session expired** | notification + opens the login page |

The check reads the `WORK TIME` card first, cuts the search at the next label so the Punch In time
is never mistaken for the Punch Out time, falls back to the first two `HH:MM:SS` values in the card
if your page renders both labels on one row, and treats `00:00`, `00:00:00` and `0:0:0` alike.
Custom regexes and two saved page samples are still available as manual overrides if the layout
ever changes.

---

## What you get

| Screen | What it does |
|---|---|
| **TTBS-HRMS** (main) | Deadline time, repeat interval, URL, optional overrides, auto sign-in, permission shortcuts, test buttons |
| **Dashboard** | Real browser view of the HRMS in desktop mode with location passed through, so you can log in and punch |
| **Hidden check** | Loads the dashboard, signs in if configured, reads WORK TIME, decides |
| **Alarm** | Full screen, lock-screen, looping alarm tone + vibration, with "open dashboard", "snooze", "stop for today" |

---

## Build the APK

The project is pinned to versions that build with **Gradle 7.4.2**, which is what AndroidIDE on a
phone ships: Android Gradle Plugin **7.3.1**, Kotlin **1.7.21**, compileSdk/targetSdk **33**.
That same combination also builds in Android Studio on a PC.

Do not raise the plugin versions unless you also raise Gradle — AGP 8.x refuses anything below
Gradle 8.2 with `Minimum supported Gradle version is 8.2`.

### On your phone (Android Code Studio / AndroidIDE)

1. Copy the `TTBS-HRMS` folder to phone storage, e.g. `Download/TTBS-HRMS`.
2. Open the IDE (Android Code Studio / AndroidIDE), open that folder as the project, let it index.
3. Build: the hammer/Build button, or the task `:app:assembleDebug`. If the editor workspace fails,
   use `sh build-on-phone.sh` from the Terminal tab instead.
4. The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Tap it and allow
   "install unknown apps" for your file manager.

`gradle.properties` is set to a 1 GB Gradle heap because phones have little RAM. If the build dies
with an out-of-memory error, raise `org.gradle.jvmargs` to `-Xmx1536m` or `-Xmx2048m`.

If your IDE already bundles Gradle 8.x, you may instead switch root `build.gradle` to
AGP `8.2.2` and Kotlin `1.9.22`, set `compileSdk 34` / `targetSdk 34`, and use
`androidx.core:core-ktx:1.12.0` and `com.google.android.material:material:1.11.0`.

### If the IDE says "Project Setup Failed"

Work through these in order:

1. **Wrong folder opened.** The folder you open in the IDE must directly contain
   `settings.gradle`, `build.gradle`, `gradle.properties` and the `app` folder. If your file manager
   unzipped into `Download/TTBS-HRMS/TTBS-HRMS`, you opened the outer one — open the inner one.
2. **Stale workspace from the failed AGP 8.2 attempt.** Use *Close Project*, delete the old project
   folder from storage, unzip this build fresh, then open it again. The previous failure is cached
   per project path.
3. **Project in shared storage.** Editors on Android sometimes fail to analyse a project that lives
   under `Download/` even with "All files access" granted. If the IDE has its own projects folder,
   copy `TTBS-HRMS` into it and open it from inside the app.
4. **Storage permission.** The IDE needs "All files access" in Android settings to read a project
   in `Download`.
5. **Heap.** If the log mentions out-of-memory, raise `org.gradle.jvmargs` in `gradle.properties`.
6. **If the log says `CONFIGURE SUCCESSFUL` but the workspace still fails**, Gradle is fine and only
   AndroidIDE's editor analysis broke. You do not need the editor to get an APK — open AndroidIDE's
   **Terminal** tab, `cd` into the project folder and run:

   ```
   sh build-on-phone.sh
   ```

   It finds Gradle on the phone, builds the debug APK, and prints the exact path when done. If it
   cannot find Gradle it prints the paths it checked, which tells us where to look.

   The script is just a wrapper around `gradle :app:assembleDebug`, so you can also run that by
   hand from the Terminal tab after `cd`-ing into the project folder.
7. **Still failing?** *View Error* only repeats the generic message, so the Build output log above it
   is the real detail. Send that log.

This build uses the classic Gradle layout (`buildscript` classpath in the root `build.gradle`,
plain `settings.gradle`) because the newer `plugins {}` / `dependencyResolutionManagement` style
needs a network round trip to the Gradle Plugin Portal, which some phone setups cannot reach.

### On a PC (Android Studio)

1. `File → Open`, pick the `TTBS-HRMS` folder, let Gradle sync.
2. If it says the Gradle wrapper is missing, point
   `Settings → Build, Execution, Deployment → Build Tools → Gradle` at the
   `gradle-wrapper.properties` file, or run `gradle wrapper` once in the folder.
3. `Build → Build Bundle(s)/APK(s) → Build APK(s)`.

---

## First run — four things

1. **Allow notifications, exact alarms, display over other apps, and location.** The buttons are in
   the app. "Display over other apps" is what lets the alarm screen appear without you touching the
   phone; without it you only get a notification.
2. **Ignore battery optimisation** for TTBS-HRMS, and on Xiaomi/Realme/Oppo/Vivo also enable
   **Autostart**, or the system will kill the alarm.
3. Tap **Open dashboard** and log in once. The app keeps the session cookies, so this is a one-time
   job — that is the "keeps me logged in" part.
4. Set your **deadline** (default 6:00 PM) and **repeat interval** (default 2 min), then flip
   **Reminder is ON**. Press **Check my punch-out status right now** to test the whole chain.

---

## Auto sign-in (optional)

Turn on **"Fill my username and password automatically"** and enter your Employee ID and password.
The password is encrypted with a key held in the Android Keystore, never stored in plain text.

The login page is: **Employee ID / Username**, **Password** with a show/hide eye, a
**"Sign In to Account"** button, and a **Cloudflare Turnstile** widget above the button. The script:

- finds the username box by its `<label>` text, name, id, placeholder and `aria-label`;
- finds the password box by `type=password`;
- writes both values through the native value setter and fires `input`/`change`/`blur`, which is
  what React/Vue-style forms actually listen to (setting `.value` alone does nothing there);
- clicks the element whose text matches *sign in / log in / submit / continue*, so it works whether
  that blue button is a real submit button or a styled `<div>`/`<a role="button">`;
- then waits for the password box to disappear (polling every 2.5 s, up to ~20 s) before reading the
  dashboard.

If it never gets past the login screen, turn auto sign-in off and rely on the saved session — that
path involves no captcha at all.

---

## Deliberately NOT included

Auto punch-out. Punching is an attendance record; faking it in software is a different thing from
reminding you. TTBS-HRMS only reminds you and opens the dashboard for you to tap.

## Honest limitations

- **Cloudflare Turnstile:** it usually clears itself with no click and does render in a WebView, but
  Cloudflare may refuse a challenge it considers automated. If auto sign-in fails, use the session.
- **"Secure Login (Company Network)":** if that footer is enforced, sign-in only works on the office
  network or VPN. Off-network you get the "couldn't confirm" alarm — tell me and I'll make it skip
  quietly when it detects that page.
- **Geofencing:** if the HRMS refuses punches away from the office, the reminder still works but you
  can only actually punch out at work.
- **Aggressive battery managers** may still delay the alarm unless autostart is allowed and battery
  optimisation is off.
- **Untested build:** there is no Java/Android SDK in the environment I wrote this in, so the APK has
  never been compiled or run. All layout ids, resource references and braces were checked, but the
  first Gradle sync may need a small fix — send me the error and I'll correct it.

## File map

```
app/src/main/java/com/ttbs/punchguard/
  MainActivity.kt      settings + permissions + tests
  BrowserActivity.kt   visible desktop-mode dashboard, sample capture
  CheckActivity.kt     hidden check: load -> sign in -> read WORK TIME -> decide
  AlarmActivity.kt     full-screen alarm with sound + vibration
  AlarmReceiver.kt     what the scheduled alarm wakes up
  AlarmScheduler.kt    exact alarms, deadline maths, repeats
  BootReceiver.kt      re-arms after reboot or app update
  Notify.kt            notification channels and full-screen-intent fallback
  WebSetup.kt          desktop UA, geolocation grant, persistent cookies
  Detect.kt            the Punch In / Punch Out decision rules
  Cfg.kt               all settings
  Crypto.kt            Android Keystore AES/GCM for the password
```
