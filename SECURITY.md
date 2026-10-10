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
