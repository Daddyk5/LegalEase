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
- **Uploaded files.** Copied to `filesDir/scans/{id}/` only until OCR finishes, then deleted.
- **Backups.** The database, document copies, and DataStore settings are excluded from cloud
  backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`); the database could
  not be decrypted on another device anyway.
- **Cloud sync.** Off by default and only runs for a signed-in user who opted in. Only analysis
  results (name, classification, confidence, summary, page counts) go to Firestore; OCR page text
  and uploaded files never leave the device. Deleted analyses sync as content-free tombstones.
