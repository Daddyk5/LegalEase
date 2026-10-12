# Security

## Reporting a vulnerability

Please do not open a public issue. Email the maintainers privately with a description,
reproduction steps, and the affected version. We aim to acknowledge reports within 72 hours.

## Secrets policy

- No API keys, signing keys, or service credentials in source, resources, `BuildConfig`, or commits.
- Machine-local values go in `local.properties` or `~/.gradle/gradle.properties`; both are git-ignored.
- `google-services.json`, `*.jks`, and `*.keystore` are git-ignored. Copy
  `app/google-services.json.example` and fill it in from your own Firebase project.

## Required action: rotate previously committed credentials

The following were committed to this repository's history and must be treated as compromised.
Removing them from the current tree does **not** remove them from git history.

| Credential | Where it was | Action |
|---|---|---|
| Gemini API key (`AIzaSyCWu_…`) | `app/build.gradle.kts`, hardcoded `resValue` fallback | **Delete the key** in Google Cloud Console → APIs & Services → Credentials, and create a new one if Gemini is still needed. The app no longer uses it. |
| Firebase Android API key (`AIzaSyCdAJ…`) | `app/google-services.json` | Firebase Android keys identify the project rather than authorize access, but restrict it: in Cloud Console, limit it to the Android app (package `com.hcdc.legalease` + release SHA-1) and to the Firebase APIs in use. App Check enforcement (Phase 3) is the real protection. |

Optionally purge both from history with `git filter-repo` and force-push. This rewrites every
commit hash and requires all collaborators to re-clone, so coordinate before doing it.
Rotation is required either way: anyone who cloned the repo already has the old values.

## Data at rest

- **Local database.** Analyses, OCR page text, and generated contracts live in a Room database
  encrypted with SQLCipher. Its 256-bit passphrase is random, generated on first launch, and stored
  in `noBackupFilesDir` sealed with AES-256-GCM under a non-exportable Android Keystore key
  (StrongBox-backed where the device supports it). If the Keystore key is lost or invalidated, the
  unreadable database is discarded and recreated rather than crashing the app.
- **Uploaded files.** Copied to `filesDir/scans/{id}/` (app-private, excluded from backup) so
  cited pages can be reopened; deleted with their analysis, by retention, or on account deletion.
- **Backups.** The database, document copies, and DataStore settings are excluded from cloud
  backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`); the database could
  not be decrypted on another device anyway.
- **Cloud sync.** Off by default and only runs for a signed-in user who opted in. Only analysis
  results (name, classification, confidence, summary, page counts) go to Firestore; OCR page text
  and uploaded files never leave the device. Deleted analyses sync as content-free tombstones.

## On-device model protection (deterrence, not DRM)

The offline language model (Gemma 3, under the Gemma Terms of Use) runs entirely on the phone. The
measures below make casual extraction harder. **They do not make extraction impossible:** the app
must decrypt the model to run it, and a determined attacker with a rooted device or a debugger can
recover it. Treat the model as protected from copying by its license, not by these controls.

| Measure | Detail |
|---|---|
| Not in the base APK | Delivered via Play Asset Delivery (`llm_pack`, fast-follow) or a one-time Wi-Fi download from Cloud Storage. |
| Integrity | Every model file is checked against a SHA-256 pinned in code (`ModelCatalog`) before use; a mismatch is deleted and re-fetched. Unpinned models are never loaded. |
| Encrypted at rest | AES-256-GCM in authenticated 1 MiB chunks (reordering, truncation, and tampering are detected) under `filesDir/.cache_core/`, excluded from backup and device transfer. |
| Key handling | A random data key sealed by a non-exportable Android Keystore key (StrongBox when available). Bulk decryption runs in software because StrongBox is too slow for it. |
| Loading | Decrypted to a temporary file in `noBackupFilesDir`, loaded by the engine, and deleted immediately. The runtime's cache directory is wiped when the engine is released. |
| Asset-pack copy | After Play delivers the pack and the encrypted copy is verified, the plaintext pack is removed. |
| Prompts and legal reference text | Not shipped as assets: encrypted at build time into generated code (`sealPrompts`) and decrypted in memory at runtime. The key ships in the app, so this is obfuscation. |
| Release build | R8 full mode with obfuscation; keep rules only for LiteRT-LM/TFLite native interfaces; `Log.d`/`Log.v` stripped. No model names appear in UI strings, file names, logs, or analytics. |

## Analysis integrity

- The model never supplies legal citations. It outputs reason codes; the app maps them to Civil
  Code articles from a reviewed table, and strips any article numbers the model writes.
- Every quote the model attributes to the contract must match the OCR text (similarity ≥ 0.85) or
  it is dropped.
- A classification is shown only at confidence ≥ 0.80 (see ARCHITECTURE.md for the formula). The
  classifier alone can never reach that threshold.

## Contract generator integrity

- The four templates are deterministic Kotlin (`core/domain/.../generator`). Their mandatory
  clauses come only from the user's form entries; the model cannot change them.
- The on-device model may only suggest optional clauses and reword optional clauses. Every
  suggestion passes `ClauseGuard`, which withholds anything that cites law, states an amount or a
  date, or (for rewording) changes a number or a party. The user is told how many were withheld.
- Accepted changes are shown as a word diff first; nothing changes until the user accepts.
- Legal notices (Arts. 1358, 1403(2)(e), 1648, 1956) are fixed, reviewed strings, never generated.
- Every saved version is analysed with the same pipeline as an uploaded contract before it can be
  exported. Versions are immutable locally and in Firestore; an edit is a new version.
- Generated contracts and their form data contain personal data (names, addresses, ID types). They
  are stored in the encrypted database and sync only for opted-in users. Exported PDFs are written
  to app cache and replaced on the next export.

## Privacy and device protection (RA 10173)

See PRIVACY.md for the user-facing policy.

- **Consent:**
  - A privacy notice must be accepted before first use. A new notice version asks again.
  - Cloud backup turns on only after a separate consent dialog.
- **Data controls:**
  - *Export my data* builds a ZIP in `cache/exports/`. It stays app-private until the user shares
    it through the share sheet.
  - Retention (30/90/365 days, or never) runs daily in the WorkManager job `retention-daily`, and
    once immediately whenever the setting changes. Deletions sync as tombstones.
  - *Delete my account*, in this order:
    1. Requires a sign-in within the last 5 minutes.
    2. Stops background work.
    3. Deletes the user's Firestore documents and `users/{uid}/backups/` in Storage.
    4. Deletes the Firebase user.
    5. Wipes the database, the documents, the exports and the settings.
  - The same deletion is available without the app at `/delete-account/` on Firebase Hosting.
- **App lock:** optional, using strong biometrics or the device credential. It locks on launch
  and again after 30 seconds in the background. Turning it on requires one successful
  authentication first.
- **FLAG_SECURE:** set while the result, page, image, PDF and contract-editor screens are shown.
  This blocks screenshots, screen recording and the recent-apps thumbnail.
- **Network:** `network_security_config.xml` disables cleartext traffic and trusts only system CAs.
- **Device integrity:**
  - Release builds request a Play Integrity token. The `integrityVerdict` Cloud Function
    (App Check enforced) decodes it server-side and returns only TRUSTED, COMPROMISED or UNKNOWN.
  - On COMPROMISED, the dashboard shows a warning and the on-device LLM is disabled. Analysis
    falls back to the classifier and rules.
  - Network or API failures leave the cached verdict unchanged. They never mark a device
    compromised.
- **Audit log:**
  - The local, encrypted `audit_log` table records each analysis (success or failure), contract
    save, export, data export and cloud-backup consent change.
  - Each entry holds the timestamp, the model version, the confidence total and its five
    components, and the scoring version. It never holds document text.
- **Dependency scanning:**
  - `.github/workflows/dependency-security.yml` submits the Gradle dependency graph and fails pull
    requests that add a dependency with a high-severity advisory.
  - Dependabot watches Gradle, npm and GitHub Actions.
