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
