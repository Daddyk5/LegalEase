# Privacy Policy

**LegalEase** reads, explains and helps draft Philippine contracts. This notice explains how
LegalEase handles personal information under the **Data Privacy Act of 2012 (Republic Act No. 10173)**,
its Implementing Rules and Regulations, and the issuances of the National Privacy Commission (NPC).

> Before publishing, fill in every `[bracketed]` placeholder. The in-app notice
> (Privacy & data → Privacy notice) summarises this document; keep the two consistent and raise
> `PrivacyNotice.CURRENT_VERSION` when the substance changes, so users are asked to accept again.

## 1. Personal information controller

| | |
|---|---|
| Controller | [Legal name of the developer or organisation] |
| Address | [Business address, Philippines] |
| Data Protection Officer | [Name], [email], [phone] |
| Last updated | [YYYY-MM-DD] |

## 2. What we process, and why

| Data | Source | Purpose | Lawful basis (RA 10173) |
|---|---|---|---|
| Email address, display name, sign-in provider | You, when you create an account | Account and sign-in | Contract (Sec. 12(b)) |
| Contract documents you upload (PDF or images) | You | Reading the text (OCR) and analysing the contract | Contract (Sec. 12(b)); consent (Secs. 12(a), 13(a)) where a document holds sensitive personal information |
| Text read from those documents | Created on your device | Analysis, and showing the cited pages | Same as above |
| Analysis results (classification, confidence, summary, reasons, Civil Code references) | Created on your device | Showing you the result and your history | Contract |
| Contracts you generate, and the form data (names, civil status, citizenship, addresses, ID types, amounts, dates, witnesses) | You | Drafting, saving, analysing and exporting the contract | Contract; you confirm you may give us the other parties' details |
| Settings (cloud backup, retention, app lock) | You | Applying your choices | Contract |
| Activity log (time, event, model version, confidence components) | Created on your device | Letting you check how each result was produced; accountability | Legitimate interest (Sec. 12(f)) |
| Crash reports, performance traces, usage events | Your device, through Firebase | Fixing bugs and keeping the app reliable | Legitimate interest (Sec. 12(f)) |
| Device integrity verdict | Google Play Integrity | Protecting the on-device model and your data on compromised devices | Legitimate interest (Sec. 12(f)) |

We **do not** process document text, contract content or form data for advertising, profiling or
model training. Usage events hold event names, counts and durations, never document content. The
activity log never holds document text.

LegalEase is **not legal advice**. Results come from automated analysis and are shown only when
their confidence meets a fixed threshold.

## 3. Where it is processed

| Where | What | Notes |
|---|---|---|
| **Your device** | Everything above | Documents, text, results, contracts and the activity log are kept in a database encrypted with SQLCipher. Its key is protected by the Android Keystore. Analysis runs on your device: **your documents are never uploaded for analysis.** |
| **Google Firebase** (Google LLC / Google Asia Pacific Pte. Ltd.), region `asia-southeast1` (Singapore) | Your account; and, **only if you turn on cloud backup**, your analysis results and saved contracts | Firebase Authentication, Cloud Firestore, Cloud Functions. Uploaded documents and the text read from them are **never** sent. |
| **Google Firebase** (Crashlytics, Performance Monitoring, Analytics) | Crash reports, traces, usage events | May be processed outside the Philippines, under Google's data processing terms. |
| **Google Play** | Integrity check | Only release builds downloaded from Google Play. |

Transfers outside the Philippines follow Section 21 of RA 10173: we stay accountable for your
data and require our processors to protect it to the same standard.

## 4. Consent to cloud backup

Cloud backup is **off** by default. You turn it on in Privacy & data, after a screen that tells you
what will be uploaded. You can turn it off at any time. Data already uploaded stays until you delete
it, or until your retention setting removes it.

## 5. How long we keep it

| Data | Retention |
|---|---|
| Uploaded documents and their text | On your device, with their analysis. Removed when you delete the analysis, when retention removes it, or when you delete your account. |
| Analysis history | Your choice: 30, 90 or 365 days, or until you delete it (the default). The deletion applies to the cloud backup too. |
| Generated contracts | Until you delete them or your account. |
| Exported files (ZIP, PDF) | Kept in the app's private cache until you share them; the next export replaces them. Once shared, the copy you sent is under your control. |
| Activity log | On your device, until you delete your account or uninstall the app. |
| Account | Until you delete it. |
| Crash and performance data | Under Firebase's retention (90 days for Crashlytics). |

## 6. Your rights

Under Sections 16–18 of RA 10173 you have the right to be informed, to object, to access, to
rectify, to erasure or blocking, to data portability, and to damages.

- **Access and portability:** Privacy & data → *Export my data*. You get a ZIP containing a JSON
  file of your account, settings, analyses, contracts and activity log, along with your documents
  and contract PDFs.
- **Rectification:** edit a contract to save a new version, or contact the DPO.
- **Erasure:**
  - Delete one analysis from your history.
  - Choose a retention period.
  - Or use Privacy & data → *Delete my account*. This removes your cloud data, your account and
    everything stored on the device.
- **Erasure without the app:** visit `[https://your-project.web.app/delete-account/]`, sign in and
  confirm. This removes your account and your cloud data. Data stored on a phone is removed when
  the app is uninstalled.
- **Objection:** turn off cloud backup. You can also contact the DPO about crash and usage data.

We answer requests sent to the DPO within [15] working days.

If you believe we have not handled your data properly, you can complain to the **National Privacy
Commission**: [privacy.gov.ph](https://privacy.gov.ph), complaints@privacy.gov.ph.

## 7. Security

- Data on your device is encrypted, as described in [SECURITY.md](SECURITY.md).
- Network traffic uses TLS only: plain-text traffic is disabled.
- Access to the cloud is limited to your own records by security rules and App Check.
- An optional biometric or screen-lock **app lock** protects the app.
- Screens showing contract content block screenshots and screen recording.
- On devices that fail Google Play's integrity check, the app warns you and turns off the
  on-device language model.

## 8. Personal data breaches

If a breach is likely to put you at real risk of serious harm, we will notify the NPC and the
affected users within **72 hours** of discovering it. The notice will describe the breach, the data
involved, the measures taken, and who to contact. This follows NPC Circular 16-03.

## 9. Children

LegalEase is not intended for anyone under 18. Contracts generally require parties of legal age.

## 10. Changes

When this policy changes materially, the app shows the new notice and asks you to accept it before
you continue.

---

## Appendix: Google Play Data safety form

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Email address, name | Yes | No | Account management | No (required for an account) |
| User IDs | Yes | No | Account management | No |
| Files and docs | **No.** Processed on the device only | No | — | — |
| Other user-generated content (analysis results, contracts) | Yes, only with cloud backup on | No | App functionality | Yes |
| Crash logs, diagnostics | Yes | No | Analytics, app functionality | No |
| App interactions | Yes | No | Analytics | No |

Also answer these questions on the form:
- Data is encrypted in transit: **Yes**.
- Users can request deletion: **Yes**, in the app and at the web URL above.
- Independent security review: **No**, unless one has been done.
