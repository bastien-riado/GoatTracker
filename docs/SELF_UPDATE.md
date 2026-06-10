# In-App Self-Update (off-store)

GoatTracker updates itself without any app store. CI publishes a **signed** APK + `version.json` to
GitHub Releases on every push to `master`; the app checks a fixed URL at startup and offers a one-tap
update. Zero infrastructure cost, no third-party tester app.

## How it works

**CI** — `.github/workflows/release.yml`, on push to `master`:
- builds a signed release APK (`versionCode = github.run_number`, `versionName = 1.0.<run_number>`),
- computes its SHA-256,
- generates `version.json`,
- publishes both as assets on a GitHub Release with `make_latest: true`.

**App** — `com.example.goattracker.update`, at launch (`UpdateGate` in `MainActivity`):
- GETs `https://github.com/bastien-riado/GoatTracker/releases/latest/download/version.json`,
- if remote `versionCode` > installed `versionCode` (and not snoozed) → shows a dialog,
- **Mettre à jour** → downloads the APK (progress bar), verifies SHA-256, launches the system
  installer (handling the per-app "install unknown apps" permission on API 26+),
- **Plus tard** → snoozes that version (a *newer* one will still prompt),
- any failure (offline, parse error, HTTP error) is **silent** (no crash, no error popup).

The `…/releases/latest/download/<asset>` path always resolves to the newest release → that's the
fixed/predictable URL.

## One-time setup: GitHub repository secrets

The release keystore was generated **outside the repo** at
`C:\Users\DrPixel\Desktop\Projects\GoatTracker-release-keys\`
(`release.jks`, `keystore-base64.txt`, `SECRETS.txt`).

> ⚠️ **Back up that folder.** If the keystore is lost you can never ship another self-update — Android
> rejects an update signed with a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

Add these 4 secrets in **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | full contents of `keystore-base64.txt` |
| `KEYSTORE_PASSWORD` | the value in `SECRETS.txt` |
| `KEY_ALIAS` | `goattracker` |
| `KEY_PASSWORD` | the value in `SECRETS.txt` (same as keystore password) |

`GITHUB_TOKEN` is provided automatically (the workflow declares `permissions: contents: write`).

## Local signed build (optional)

Put the same values in `local.properties` (gitignored):

```
RELEASE_STORE_FILE=C:\\Users\\DrPixel\\Desktop\\Projects\\GoatTracker-release-keys\\release.jks
RELEASE_STORE_PASSWORD=<from SECRETS.txt>
RELEASE_KEY_ALIAS=goattracker
RELEASE_KEY_PASSWORD=<from SECRETS.txt>
```

The build resolves signing from env vars first (CI), then these `local.properties` keys (dev).

## Testing end-to-end

1. Add the 4 repository secrets.
2. Merge this feature into `master` → CI publishes **v1** (contains the update checker).
3. Install that APK on a device (from the GitHub Release page).
4. Merge another change (heatmap / mini-player) into `master` → CI publishes **v2**.
5. Reopen the installed app → it detects v2 → tap **Mettre à jour** → system installer.

## Dev vs prod (side-by-side installs)

The **debug** build uses `applicationId com.example.goattracker.dev` and the name **GoatTrackerDev**
(`applicationIdSuffix = ".dev"` + a per-build-type `resValue` for `app_name`, with `buildFeatures.resValues = true`).
The **release** build stays `com.example.goattracker` / **GoatTracker**. They therefore install side by
side instead of colliding with the *"package already exists" / INSTALL_FAILED_UPDATE_INCOMPATIBLE*
signature clash (same id, different signing key). The self-update check is **skipped on debuggable
builds**, so only the prod app self-updates.

> The **release** `applicationId` is the prod identity — changing it later breaks updates (different
> package), so keep `com.example.goattracker` stable.
