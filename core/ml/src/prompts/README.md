# Model prompts

Plaintext sources for the on-device model's prompts and Civil Code reference text.

They are **not** packaged as assets. At build time, the `sealPrompts` task in `core/ml/build.gradle.kts`
encrypts each `.txt` file with AES-256-GCM into generated Kotlin (`SealedPrompts`), which `PromptVault`
decrypts at runtime. This keeps prompts out of plain sight in the APK. It is deterrence, not secrecy:
the key ships in the app. See SECURITY.md.

Rules for editing:
- Never instruct the model to cite article numbers; the app attaches citations itself.
- Keep the reference text short; it is sent with every reasoning pass.
- Have a lawyer review changes to `civil_code_reference.txt`.
