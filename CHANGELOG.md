# Changelog

All notable changes to LegalEase are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Add new entries under "Unreleased".

## [Unreleased]

The production upgrade, done in eight phases. Nothing has been released from it yet.

### Added
- **CI/CD and docs:**
  - GitHub Actions for pull requests: build, lint, detekt, unit tests, coverage gate, rules and
    Functions tests.
  - A tagged-release workflow that builds a signed bundle, with optional Play upload through
    Gradle Play Publisher.
  - Issue and pull request templates, CODEOWNERS, and CONTRIBUTING.md with branch protection
    recommendations.
  - README, ARCHITECTURE.md, a threat model in SECURITY.md, and this changelog.
- **Quality:**
  - Compose UI tests and an end-to-end OCR pipeline test, plus a baseline profile and a
    cold-start benchmark.
  - A 70% coverage gate, ktlint through detekt, and StrictMode and LeakCanary in debug builds.
- **Filipino translation** of every screen. All UI text is now in string resources.
- **Privacy (RA 10173):**
  - Privacy notice and opt-in cloud backup.
  - Data export, retention settings, and account deletion in the app and on the web.
  - App lock, and `FLAG_SECURE` on sensitive screens.
  - Play Integrity check, a local audit log, and dependency scanning.
- **Contract generator** with four Philippine templates (lease, loan, deed of absolute sale,
  lease of personal property):
  - Validated forms, guarded clause suggestions with a diff, and immutable versions.
  - Self-analysis of every version, and PDF export with the logo header.
- **On-device language model** (Gemma 3 through LiteRT-LM):
  - Analysis extracts facts per page, then reasons three times for self-consistency.
  - Quotes are verified against the OCR text, and citations come from a reviewed Civil Code table.
  - Confidence is scored from five components, with an 80% display threshold.
- **Firebase:**
  - Email and Google sign-in with verified accounts.
  - Firestore and Storage security rules with tests, and App Check.
  - Remote Config kill switches, Crashlytics, Performance and Analytics.
  - Paged history.

### Changed
- Restructured into a multi-module clean architecture with Hilt, an encrypted Room database
  (SQLCipher) and WorkManager. Analysis runs as foreground work and survives the app being
  backgrounded.
- The offline TFLite classifier is now loaded and used. OCR got page preprocessing and quality
  scoring.
- Buttons grow with the system font size, and text links have 48 dp touch targets.

### Removed
- The cloud Gemini integration and its hardcoded API key. Contract analysis no longer sends
  documents off the device.

### Security
- A Gemini API key was committed to the repository's history and must be rotated (SECURITY.md).
