# Testing

## Automated tests

```sh
./gradlew assembleDebug lint detekt testDebugUnitTest    # build, static analysis, JVM unit tests
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
