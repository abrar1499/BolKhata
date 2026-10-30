# Smart Expense (Android)

Bolo aur hisaab khud apni jagah chala jaye. 🎤 dabao, bolo "Ahmed ko 200 riyal udhaar diye", entry ban jayegi.

## Native offline intelligence
- Chat interpretation runs in `NativeChatEngine.kt`; it has no Gemini, Groq, OpenRouter, Claude, or hosted LLM calls.
- English, Roman Urdu, Hindi/Urdu transliteration, saved contact names, number words, dates, categories, and follow-up context are interpreted on-device.
- Queries and totals are computed from the local ledger by `Query.kt` and `Questions.kt`; changes and deletes go through the existing guarded action/Undo flow.
- Photos and scanned PDFs use the bundled ML Kit Latin OCR model. Bank statements are accepted only when the local parser reconciles them; receipts are accepted only when a printed total is detected. Arabic-only OCR text may require manual review.
- Chat, entries, contacts, and learned word fixes remain in local app storage. Legacy hosted-AI keys are cleared during upgrade. Manual update checks and Android's speech-recognition service are separate system features.

## APK build
1. Pull requests run unit tests and build an APK artifact for review.
2. A release is published only by an explicitly approved `[build-apk]` commit pushed to `main`.
3. Download `BolKhata-apk` from the successful workflow run, or use the latest GitHub Release.

## Android Studio
Open the folder in Android Studio, sync Gradle, connect a phone, and press ▶ Run.

## Account safety and recovery
- Khata history supports Select all, search, and guarded bulk moves.
- Move history and restore refuse stale restores after later edits or moves.
- Rename and merge are separate actions; merge previews affected entry counts.
- Chat/voice commands stay scoped to the open khata. Ambiguous multi-entry delete/move requests require clarification.
- App interface language and speech-recognition language are independent.

Release workflow runs unit tests and builds a signed release APK. Install and test on your own phone before relying on it for important financial records.

## Chat diagnostics and bug reports
- The Assistant chat header has a Share Diagnostics action. The user must explicitly confirm before a report is created.
- The report is a plain-text attachment containing up to the latest 100 chat messages, local engine intent/match traces, app version, and Android device metadata.
- It excludes ledger rows, contact lists, credentials, and attachment file bytes. Chat text can still contain personal or financial details; review before sharing.
- Share the report to ChatGPT to request debugging. Reports are not sent automatically and the app does not provide a live connection to the developer.
