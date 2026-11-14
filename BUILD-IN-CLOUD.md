# Build the APK for free, in the cloud (no PC needed)

Your phone can't compile this project reliably, and you shouldn't pay anyone to compile it. GitHub
will do it on their servers for free and hand you the APK.

Free means: **public repository = unlimited build minutes**, and a private repository gets
**2,000 free minutes a month** on the GitHub Free plan. One build here takes about 2-4 minutes.

This project is safe to make public: there are no passwords, tokens or URLs of your own inside it.
The employee ID and password are typed into the app at runtime and stored on your phone, never in the
code.

---

## 1. Make a free GitHub account

github.com -> Sign up. Skip any paid plan; everything below works on the free one.

## 2. Create a repository

- github.com -> **New repository**
- Name: `ttbs-hrms`
- **Public** (unlimited free minutes) — or Private if you prefer, still free within the monthly quota
- Do **not** tick "Add a README file"
- **Create repository**

## 3. Upload this project into it

On the empty repository page, click **uploading an existing file**, then drag in **everything from
this folder** — including the `.github` folder, which is the important part. If your file manager
cannot drag folders, install **GitHub Desktop** on any computer, or use the GitHub mobile app, and
upload the whole folder that way.

The `.github/workflows/build-apk.yml` file is the recipe; it must end up at that exact path in the
repository.

## 4. Let it build

- Open the **Actions** tab of the repository.
- A run called **Build APK** starts by itself. If it doesn't, click *Build APK* -> *Run workflow*.
- Wait 2-4 minutes. A green tick means the APK was built.

## 5. Download and install

- Open the finished run, scroll to **Artifacts**, tap **TTBS-HRMS-debug-apk**.
- It downloads a `.zip`; inside is `app-debug.apk`.
- Open that APK on the phone and allow "install unknown apps" when asked.

Artifacts stay available for 90 days, so you can come back and re-download.

---

## If the build goes red

Open the failed step and copy the red error lines. The two most likely ones:

| Error | Meaning |
|---|---|
| `SDK location not found` | the workflow file didn't reach `.github/workflows/` — check the path |
| `Unresolved reference: ...` | a Kotlin compile error; send me the line and I'll fix the code |

## After it installs

1. Allow **notifications**, **exact alarms**, **display over other apps**, **location**.
2. Ignore battery optimisation for it; on Xiaomi/Realme/Oppo/Vivo enable **Autostart** too.
3. Tap **Open dashboard**, log in once — the session is remembered.
4. Set the deadline (6:00 PM default) and repeat interval (2 min default), switch the reminder on, then
   press **Check my punch-out status right now** to see the alarm fire.

## The other free routes, for the record

- **Android Studio on any Windows/Mac/Linux PC** — free, and the most reliable. Open this folder,
  `Build -> Build APK`.
- **Android Code Studio on the phone** — free and open source, but its editor workspace is failing on
  this project on your device; the cloud build above sidesteps that entirely.
