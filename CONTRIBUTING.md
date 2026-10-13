# Contributing

## Getting started

Follow [SETUP.md](SETUP.md) to get a Firebase-connected debug build running. Read
[ARCHITECTURE.md](ARCHITECTURE.md) before changing the analysis pipeline or adding a module.

## Ground rules

- **Do not change the design.** Keep layouts, colors (`Color.kt`), typography (`Type.kt`),
  components and navigation as they are. Polish within the design system is welcome: spacing,
  loading, empty and error states, accessibility, dark mode. Describe it in the pull request.
- **No secrets in git:** no API keys, keystores, `google-services.json`, or service-account files.
  See [SECURITY.md](SECURITY.md).
- **No real contracts in git, issues or pull requests.** Use made-up samples.
- **Honest output.** Never fabricate, clamp, floor or hardcode confidence values, legal citations
  or clauses. Citations come only from the reviewed table in `CivilCodeCitations`.
- **Never lower the 0.80 display threshold.** If calibration falls short, tighten the weights
  (TESTING.md, "Confidence calibration").
- **Strings:** all user-facing text lives in string resources, in English (`values/`) and
  Filipino (`values-fil/`).

## Workflow

1. Branch from `main`: `feat/short-name`, `fix/short-name`, `docs/short-name`.
2. Commit with [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`,
   `refactor:`, `test:`, `docs:`, `build:`, `chore:`. The pull request title follows the same form.
3. Before pushing, run:
   ```sh
   ./gradlew assembleDebug lint detekt testDebugUnitTest
   ./gradlew :core:domain:koverVerify :core:ml:koverVerifyDebug
   ```
   detekt includes ktlint formatting rules. `./gradlew detekt --auto-correct` fixes most of them.
4. Add an entry under "Unreleased" in [CHANGELOG.md](CHANGELOG.md).
5. Open a pull request and fill in the template. CI must pass and a code owner must approve.

## Releasing

1. Move the "Unreleased" entries in CHANGELOG.md under a new version heading.
2. Write release notes from `.github/release-notes-template.md`.
3. Tag and push: `git tag v1.2.0 && git push origin v1.2.0`.
4. The **Release** workflow builds the signed bundle and keeps it as a workflow artifact for 90
   days. If the Play secret is set, it also uploads the bundle to the internal testing track.
   Promote it from there in Play Console.

The secrets the workflow needs are listed in SETUP.md §10d.

## Recommended repository settings

These are configured in GitHub, not in files, so they are listed here for the repository admin.

**Branch protection for `main`** (Settings → Rules → Rulesets, or Branches → Add rule):

| Setting | Value |
|---|---|
| Require a pull request before merging | On, 1 approval |
| Dismiss stale approvals when new commits are pushed | On |
| Require review from Code Owners | On (see `.github/CODEOWNERS`) |
| Require status checks to pass | On, and require the branch to be up to date |
| Required checks | `Build, lint, unit tests, coverage`, `Firestore and Storage rules`, `Cloud Functions`, `review` (Dependency security) |
| Require conversation resolution | On |
| Require linear history | On (squash or rebase merges) |
| Allow force pushes and deletions | Off |
| Apply to administrators | On |

**Tags:** add a ruleset for `v*` that allows only maintainers to create tags and blocks deleting
or moving them. A tag is what triggers a signed release.

**Environment:** create an environment named `release` (Settings → Environments). Put the
release secrets there rather than in repository secrets, add required reviewers, and limit it to
`v*` tags. Pull-request workflows can then never read them.

**Security** (Settings → Code security):
- Private vulnerability reporting: on (SECURITY.md relies on it).
- Dependabot alerts and security updates: on.
- Secret scanning and push protection: on.
