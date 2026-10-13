# Testing

## Automated tests

```sh
./gradlew assembleDebug lint detekt testDebugUnitTest    # build, static analysis (incl. ktlint), JVM unit tests
./gradlew :core:domain:koverVerify :core:ml:koverVerifyDebug   # line coverage gate, at least 70%
cd firebase-tests && npm ci && npm test                  # Firestore and Storage rules on the emulators
```

Unit tests cover the pieces that decide what users see: OCR preprocessing and quality scoring,
quote grounding, the reason-code → Civil Code table, confidence scoring and bands, the rule-based
fallback, strict-JSON parsing of model output (valid, malformed, missing fields, out-of-range
values), encryption round-trips and tamper detection, storage mappers, and every ViewModel. For
the contract generator: every template renders the parties, witnesses, and acknowledgment and never
cites law; form validation (required fields, amounts, date order, the 99-year lease limit, witnesses
who are parties); the legal notices; peso amounts in words; the word diff; and the clause guard
(law citations, amounts, dates, changed numbers or parties are withheld).

PDF layout uses Android text measurement, so it is checked by hand: export each template in A4 and
Legal, and confirm the logo header, page numbers, footer, that headings never end a page, and that
the acknowledgment starts on its own page.

Privacy and device protection are covered by unit tests:
- Privacy use cases: consent, retention, export, deletion.
- Audit entries on analysis success and failure.
- The integrity verdict disabling the LLM.
- Audit-log mappers.
- The settings ViewModel.
- `functions/` tests the integrity verdict classification (`npm test`).

Check these by hand on a device:

1. **First launch:** the privacy notice appears. *Exit* closes the app; *Accept* continues and
   does not show the notice again.
2. **Cloud backup:** the switch shows the consent dialog. *Cancel* leaves backup off.
3. **App lock:** turning it on asks for authentication. Background the app for over 30 seconds
   and it locks; under 30 seconds (for example, while picking a file) it does not.
4. **FLAG_SECURE:** screenshots are blocked and the recent-apps thumbnail is blank on a result,
   a page, a document and the contract editor. Screenshots still work on the dashboard.
5. **Export my data:** the share sheet opens with a ZIP. It contains `legalease-data.json`,
   `documents/` and `contracts/`.
6. **Retention:** choosing 30 days removes older analyses at once, and the snackbar shows the
   count.
7. **Delete my account:**
   - More than 5 minutes after sign-in, the app asks you to sign in again.
   - After deleting, you land on the intro screen, the Firestore documents are gone, and the
     account no longer signs in.
   - Repeat the deletion with the web page at `/delete-account/`.
8. **Integrity (release build from a Play testing track):** a rooted device or emulator without
   Play certification shows the warning, and analysis reports no LLM use.

### On a device or emulator

```sh
./gradlew connectedDebugAndroidTest
```

- **Compose UI tests:** each test runs a real screen and its real ViewModel on the domain fakes,
  so neither Hilt nor Firebase is involved.
  - Result screen: a confident verdict, and the "Inconclusive — needs review" state.
  - History: the list, and search.
  - Generator: every template is offered, and the form refuses to advance past missing fields.
- **Pipeline test (`:app` `AnalysisPipelineTest`):** draws a 3-page contract into a PDF, then
  runs real PdfRenderer, preprocessing and ML Kit OCR, then the analysis, and checks that it
  returns a non-null report.
  - The classifier and the LLM are faked as unavailable, because their models download at
    runtime.
  - The report therefore comes from the rule-based fallback, which is what a fresh offline
    install produces.
- **Picking a file:** the scan screens start from the system file picker, which instrumented
  tests cannot drive. The path from scan to result is covered by the pipeline test plus the
  result screen test.

### Performance

```sh
./gradlew :app:generateBaselineProfile                          # writes app/src/release/generated/baselineProfiles
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # cold start, with and without the profile
```

- Both need a physical device for meaningful numbers. Profile generation needs API 33+, or a
  rooted device on API 28+.
- The cold-start target is under 1.5 s to first frame on a mid-range device, excluding
  first-time model setup.

| Device | Without profile (median) | With profile (median) |
|---|---|---|
| _not measured yet_ | | |

### Accessibility

**Automated or built in:**
- Every actionable icon has a content description; decorative images have none, so TalkBack
  skips them.
- Text links have 48dp touch targets (`minimumInteractiveComponentSize`).
- Buttons grow with the font size instead of clipping.
- All UI strings are resources, in English and Filipino. Legal terms keep the English in
  parentheses.

**Checked by hand:**
1. TalkBack: walk through sign-in, upload, the result (reasons and page links), history
   (delete is a custom action) and the generator form. Every control must be announced with
   its purpose.
2. Font size: set it to the maximum and display size to the largest, then check that no text
   is clipped on the dashboard, result, generator and settings screens.
3. Language: switch the app to Filipino (Settings, then Apps, then LegalEase, then Language).

**WCAG AA contrast** (4.5:1 for normal text, 3:1 for large text). Measured on the existing
theme. These are flagged for a design decision; the colors were not changed:

| Pair | Ratio | Result |
|---|---|---|
| Primary `#6495ED` text on white background (light: titles) | 2.97 | Fails normal text; passes large text (3:1) only barely, at 2.97 |
| White on primary `#6495ED` (light: button labels) | 2.97 | Fails |
| Primary on surface `#F5F5F5` (light: card titles) | 2.73 | Fails |
| Outline `#B0B8C1` on white (text-field border) | 2.01 | Fails the 3:1 non-text minimum |
| Risk label, medium `#F9A825` at 70% opacity on surface (light) | 1.53 | Fails |
| Risk label, low `#2E7D32` at 70% opacity on surface (light) | 2.78 | Fails |
| Risk label, high `#C62828` at 70% opacity on surface (light) | 3.28 | Large text only |
| Risk labels, low and high, at 70% opacity on surface (dark) | 2.24 / 2.02 | Fail |
| Body text, on-background and on-surface (light and dark) | 12–18 | Pass |
| Dark theme primary on background, and on-primary on primary | 10.9 / 8.3 | Pass |

### Coverage

Kover enforces at least 70% line coverage on `:core:domain` and `:core:ml`. Run
`koverHtmlReport` (or `koverHtmlReportDebug` for `:core:ml`) to see the report.

`:core:ml` excludes two kinds of code from the measurement; the full list is in
`core/ml/build.gradle.kts`:
- Code generated by Hilt and the prompt sealer.
- Thin wrappers over native runtimes and Android APIs that cannot run on the JVM: LiteRT and
  LiteRT-LM, ML Kit, PdfRenderer, ImageDecoder, Play Asset Delivery and Firebase Storage.

The logic around those wrappers is tested through interfaces, for example `TextGenerator` and
`ModelAvailability` for the on-device LLM.

### Debug-build checks

- StrictMode logs disk and network access on the main thread, and leaked closeables, under the
  `StrictMode` tag.
- LeakCanary shows a notification when an activity, fragment or ViewModel leaks.

Neither is included in release builds.

The on-device model itself cannot run in JVM tests. Its behaviour is measured by the calibration
procedure below.

## Confidence calibration

LegalEase shows a classification only when its confidence score is at least **0.80**. That
promise is only meaningful if verdicts shown at that level are actually right at least 80% of the
time. This procedure measures it.

### 1. Build the labelled set

- Collect **30–50 real Philippine contracts** across the four supported types (lease, loan, sale,
  lease of personal property) and all five classifications. Include imperfect scans: phone photos,
  faint copies, multi-page documents.
- Have a **licensed lawyer (law adviser)** label each contract with its classification under the
  Civil Code and the decisive clause(s). Record the adviser's name and the date.
- Remove or redact personal information before storing the set. Keep it outside this repository.

### 2. Run the analysis

For each contract, on a mid-range device with the primary model tier:

1. Scan it in the app exactly as a user would.
2. Export the stored report (debug build: Analysis → `reportJson`), which includes the candidate
   label, the total score, and all five confidence components.
3. Record: adviser label, candidate label, total score, band, components, method, and page count.

### 3. Measure

| Metric | Definition |
|---|---|
| Coverage | Share of contracts that received a shown verdict (score ≥ 0.80). |
| Accuracy at ≥ 0.80 | Of the shown verdicts, the share that match the adviser's label. **Must be ≥ 80%.** |
| Accuracy at ≥ 0.90 | Same, for "High confidence" verdicts. Should be noticeably higher. |
| Inconclusive rate | Share shown as "Inconclusive — needs review". |

### 4. Act on the result

- **Accuracy at ≥ 0.80 below 80%:** tighten the weights in `ConfidenceScorer` (e.g. raise
  `W_GROUNDING` or `W_SELF_CONSISTENCY`, lower `W_AGREEMENT`) and re-measure. **Never lower the 0.80
  display threshold** to raise coverage.
- **Weights changed:** bump `ReportJson.SCORING_VERSION`, so results scored under the old weights
  are not compared with new ones.
- Commit the new weights with the results table below.

### Results

| Date | Model tier | Contracts | Coverage | Accuracy ≥ 0.80 | Accuracy ≥ 0.90 | Weights version |
|---|---|---|---|---|---|---|
| _Not yet measured_ | | | | | | |

Do not publish accuracy figures anywhere (README, papers, store listing) until this table has
real measurements.
