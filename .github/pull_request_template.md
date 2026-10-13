## What and why

<!-- What this changes, and the problem it solves. Link the issue: Fixes #123 -->

## How it was tested

<!-- Unit tests added or changed, device checks from TESTING.md that you ran, and on what device. -->

## Checklist

- [ ] The title is a conventional commit (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, `chore:`).
- [ ] `./gradlew assembleDebug lint detekt testDebugUnitTest` passes locally.
- [ ] No secrets, keys, `google-services.json`, model files, or real contracts are included.
- [ ] Screens use existing components and theme tokens; any visual polish is described above.
- [ ] New user-facing text is in string resources, in English and Filipino.
- [ ] Analysis output stays honest: no fabricated, clamped, or hardcoded confidence values, citations, or clauses.
- [ ] If confidence weights changed: `ConfidenceScorer.VERSION` was raised and TESTING.md has new calibration results.
- [ ] If what is collected, where it goes, or how long it is kept changed: PRIVACY.md and `PrivacyNotice.CURRENT_VERSION` were updated.
- [ ] CHANGELOG.md has an entry under "Unreleased".
