# Setup

Gets a new developer from a fresh clone to a running, Firebase-connected debug build. Expect about
30 minutes, most of it in the Firebase console.

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Gradle runs on 21; the code targets Java 17. |
| Android SDK | Platform 37, Build-Tools 36+ | compileSdk 37, targetSdk 35, minSdk 31. |
| Node.js | 22+ | Only for the security-rules tests. |
| Firebase CLI | 15+ | `npm install -g firebase-tools`, then `firebase login`. |

## 2. Create the Firebase project

1. In the [Firebase console](https://console.firebase.google.com), create a project and enable
   Google Analytics when asked (Analytics, Crashlytics, and Performance depend on it).
2. **Add an Android app** with package name `com.hcdc.legalease`.
3. **Add SHA fingerprints** (Project settings → Your apps → Add fingerprint). Google Sign-In and
   App Check need both SHA-1 and SHA-256:
   - Debug key: `./gradlew :app:signingReport` and copy the `debug` variant's SHA-1 and SHA-256.
   - Release: once the app is on Play, copy the **app signing key** fingerprints from
     Play Console → Test and release → App integrity. (Not your upload key.)
4. Download `google-services.json` into `app/`. It is git-ignored; never commit it.
   `app/google-services.json.example` shows its shape.
   Re-download it after step 3.2 below, so it contains the Google web OAuth client.
5. Point the CLI at the project: `firebase use --add` (choose the project, alias `default`).

## 3. Authentication

1. Build → Authentication → Get started.
2. Sign-in method → enable **Email/Password** (leave "Email link" off).
3. Sign-in method → enable **Google**, set the support email, save.
   This creates the web OAuth client the app reads as `default_web_client_id`.
4. Templates → **Email address verification**: set the sender name to "LegalEase". The app
   blocks unverified accounts, so this email is required, not optional.
5. Settings → User actions: keep **Email enumeration protection** on. The app's password reset
   and login messages are written to not reveal whether an account exists.

## 4. Cloud Firestore

1. Build → Firestore Database → Create database, **production mode**, location
   `asia-southeast1` (Singapore), the closest region to the Philippines.
2. Deploy rules and indexes from the repo root:
   ```sh
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   Rules (`firestore.rules`) allow only the verified owner to read and write
   `users/{uid}`, `users/{uid}/analyses/{id}`, and `users/{uid}/contracts/{id}`, and validate
   every field's type and size. OCR text and documents are never stored in Firestore.

## 5. Cloud Storage

1. Build → Storage → Get started, same region as Firestore.
2. Deploy rules: `firebase deploy --only storage`.
   `models/` is read-only for verified users; `users/{uid}/backups/` accepts owner-only PDFs ≤ 20 MB.

## 6. App Check

1. Build → App Check → Apps → your Android app → **Play Integrity** → Register.
   Play Integrity requires the app to be linked to a Google Cloud project with the
   Play Integrity API enabled (the console offers to do this) and the SHA-256 from step 2.3.
2. **Debug builds** use the debug provider. Run the debug app once, then find this line in Logcat:
   `Enter this debug secret into the allow list in the Firebase Console for your project: <token>`.
   Add it under App Check → Apps → ⋮ → Manage debug tokens. Each developer device needs its own.
3. Watch App Check → APIs → Cloud Firestore / Cloud Storage metrics until verified requests
   dominate, then click **Enforce** for both. Unverified clients are rejected from then on.

## 7. Offline classifier model (Firebase ML)

1. Build → Machine Learning → Custom → Add custom model, name it exactly **`model_small_int8`**,
   and upload the `.tflite` file. The app downloads it on first launch over any network.
2. The model must take token IDs shaped `[1, N]` (INT32 or FLOAT32) and output `[1, 5]` FLOAT32 in
   the order Void, Voidable, Unenforceable, Rescissible, Enforceable. The app checks this at load
   time and shows "Offline analysis is unavailable…" if it does not match.
3. `core/ml/src/main/assets/vocab.json` must be the vocabulary the model was trained with.

> Firebase ML is deprecated and shuts down on **2027-06-15**. The model will move to Cloud Storage
> (`models/`, already covered by the rules above) before then.

## 7b. On-device language model

The offline explanation uses **Gemma 3** in LiteRT-LM (`.litertlm`) format, int4:

| Tier | Model | File name in the app | Used on |
|---|---|---|---|
| Primary | Gemma 3 1B instruction-tuned, int4 | `core-a.bin` | about 6 GB RAM or more |
| Lite | Gemma 3 270M instruction-tuned, int4 | `core-b.bin` | smaller devices |

1. Accept the **Gemma Terms of Use** with your Google account on Kaggle or Hugging Face, then
   download the LiteRT-LM community builds of both models. Keep a copy of the terms: redistributing
   the model inside the app requires passing on its use restrictions and notice.
2. Rename the files to `core-a.bin` and `core-b.bin`. Neutral names keep model names out of the app.
3. Pin them: compute `sha256sum core-a.bin core-b.bin` and the byte sizes, and set `sha256` and
   `sizeBytes` in `core/ml/src/main/java/com/hcdc/legalease/core/ml/llm/ModelCatalog.kt`. Until both
   are pinned the app never loads a model and every analysis is "needs review" (classifier + basic
   checks).
4. Deliver them:
   - **Play Store builds:** copy both files into `llm_pack/src/main/assets/models/` (git-ignored)
     before `./gradlew bundleRelease`. They ship in the `llm_pack` fast-follow asset pack.
   - **Debug and sideloaded builds:** upload both to Cloud Storage at `models/core-a.bin` and
     `models/core-b.bin` (Console → Storage → Upload). The app downloads its tier once, on Wi-Fi.
5. Verify: install, connect to Wi-Fi, and wait for the background download. Then scan a contract;
   progress should show "Analyzing page X of Y". While the model is not ready, results say
   "Preparing offline analysis…".

## 8. Remote Config

Deploy the parameters and defaults from the repo:

```sh
firebase deploy --only remoteconfig
```

| Key | Type | Default | Purpose |
|---|---|---|---|
| `enable_generator` | Boolean | `true` | Kill switch for the contract generator. |
| `enable_llm` | Boolean | `true` | Kill switch for the on-device LLM; falls back to the classifier. |
| `confidence_threshold_text` | String | "A classification is shown only when LegalEase is at least 80% confident." | Display text only; the 0.80 threshold is fixed in code. |

The same defaults ship in the app (`core/data/src/main/res/xml/remote_config_defaults.xml`), so
the app works before the first fetch and offline. Changes reach running apps in real time.

## 9. Crashlytics, Performance, Analytics

- Release & Monitor → **Crashlytics**: click Enable. Crashes appear after the first release-build
  crash and relaunch.
- Release & Monitor → **Performance**: enable. Custom traces: `analysis_end_to_end`,
  `ocr_page` (per page), `classify`.
- Analytics events: `scan_started`, `analysis_completed`, `analysis_failed`, `analysis_deleted`,
  `login`, `sign_up`, `contract_saved`, `contract_exported`, `clause_assist`. Parameters are enums,
  counts, and durations only; no document content.

## 10. Play Console (before the first release)

- **Foreground service declaration**: the app uses the `dataSync` foreground-service type for
  on-device document analysis that continues in the background. Declare it under
  App content → Foreground service permissions, with a short screen recording of a multi-page scan.
- **Data safety**: fill it in from the appendix of PRIVACY.md.
- **Privacy policy URL**: publish PRIVACY.md (for example, as a page on Firebase Hosting) and
  enter its URL under App content → Privacy policy.
- **Account deletion URL**: enter `https://<project-id>.web.app/delete-account/` under
  App content → Data deletion (see 10b).

## 10b. Cloud Functions and Hosting (integrity check and web account deletion)

Cloud Functions requires the **Blaze** (pay-as-you-go) plan. Without it the app still works: the
integrity verdict stays "unknown" and nothing is disabled.

1. **Play Integrity verdicts.** In Google Cloud console, for the project linked in step 6:
   - Enable the **Google Play Integrity API**.
   - In Play Console → Test and release → App integrity → Play Integrity API, link that same
     Cloud project.
   - Grant the Functions runtime service account (`<project-id>@appspot.gserviceaccount.com`)
     the **Service Usage Consumer** role.
2. **Deploy the function** (region `asia-southeast1`; App Check is enforced on it):
   ```sh
   cd functions && npm ci && npm test && cd ..
   firebase deploy --only functions
   ```
   Only release builds call it; debug builds always report "unknown".
3. **Web account deletion page** (`public/delete-account/`). It reads the Firebase config from
   Hosting's `/__/firebase/init.json`, so no key is stored in git. Deploy it with:
   ```sh
   firebase deploy --only hosting
   ```
   - If App Check is enforced for Firestore or Storage, the page needs a web App Check key:
     1. In App Check → Apps, add the **web app** with **reCAPTCHA Enterprise**.
     2. Copy `public/delete-account/appcheck-config.example.js` to `appcheck-config.js` (git-ignored).
     3. Put the site key in that file, then redeploy.
   - Add your Hosting domain under Authentication → Settings → Authorized domains.

## 11. Run and verify

```sh
./gradlew :app:installDebug
```

1. Open the app → Begin → Create an Account. Use a real inbox; open the verification link.
2. Log in. You should land on the dashboard with your name (or email) shown.
3. Upload a PDF. You should see "Reading page X of Y", then a result. On first launch this needs
   network access once, for the model download.

## 12. Tests

```sh
./gradlew assembleDebug lint detekt testDebugUnitTest      # Android build and unit tests
cd firebase-tests && npm ci && npm test                    # Security rules, on the emulators
cd functions && npm ci && npm test                         # Integrity verdict classification
```

The rules tests start the Firestore and Storage emulators automatically using the demo project
`demo-legalease`; they never touch your real project.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Build fails: `default_web_client_id` not found | `google-services.json` is missing the web OAuth client. Enable Google sign-in (3.3) and re-download it. |
| "Google sign-in isn't available on this device" | Missing SHA-1 for the key that signed this build, or no Google account on the device. |
| "Please verify your email first" after verifying | Log in again; the verified flag is read at sign-in. |
| Sync never happens | Cloud backup is off by default. Turn it on in Privacy & data (settings icon on the dashboard); it needs a verified user. |
| "For your security, sign out and sign in again…" when deleting | Firebase requires a sign-in within the last 5 minutes. Use the snackbar's *Sign out* action, sign back in, and retry. |
| Integrity warning on a normal phone | The verdict is cached for a day. Check that the build was installed from Play; sideloaded release builds fail the app-recognition check. |
| `PERMISSION_DENIED` from Firestore in debug | App Check is enforced and this device's debug token is not registered (6.2). |
| "Offline analysis isn't ready yet" | The model has not downloaded yet; connect once. Check the model name in step 7. |
