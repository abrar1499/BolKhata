# BolKhata (Smart Expense) — notes for Claude

Android khata/expense app, Kotlin + Jetpack Compose, single module `app/`. Package `com.bolkhata.app`.

## Talking to the owner
- Reply in short, simple Hinglish (Roman Urdu). No long explanations unless asked.
- Ask before every release. Never release without a clear "haan/release kar do".
- Screenshots/mock-ups only when asked; they are expensive. When the owner asks to see a design, show it as an
  HTML mock-up Artifact (quick, no build); use the screenshots workflow only if they ask for real screenshots.

## Build and release (no Android SDK in the cloud container)
- Code cannot be compiled locally. GitHub Actions (`.github/workflows/build.yml`) is the compiler and test runner.
- Work on the session branch. Do NOT run a separate test build before release: the release build runs
  `testDebugUnitTest assembleRelease` and publishes nothing if anything fails.
- Release = put the branch tree on `main` in one commit whose message contains `[build-apk]`:
  ```
  git fetch origin main <branch>
  git checkout -B release-tmp origin/main
  git read-tree -u --reset origin/<branch>
  git commit -m "[build-apk] <what changed>"
  git push origin HEAD:main
  ```
  Releases count up by one (APP_VERSION = last release + 1, shown as "1.N" everywhere).
- Wait for the result once (background check), report once. Don't poll repeatedly.
- Screenshots (only when asked): run the Build APK workflow by hand (workflow_dispatch) on the branch; its
  `screenshots` job renders real screens (`app/src/test/.../screenshots/AppScreenshots.kt`, Robolectric +
  Roborazzi) and commits PNGs to `docs/screenshots/`.

## Privacy (the repo is PUBLIC)
- Never put real names, amounts or chat text from the owner's chat log into code, tests, comments or commit
  messages. Use made-up names (e.g. Nargis Salim Rashid, Hamid Akram).
- The chat log lives in the private repo `abrar1499/bolkhata-logs` (`chat-logs/YYYY-MM-DD.jsonl`,
  `markedWrong` = 👎, `markedGood` = 👍). `REVIEWED.md` there records what was already fixed.
- Groq key and Chat review token stay on the phone and are excluded from backups.

## How the app thinks
- Chat: optional Groq correction first (`Groq.correct`, only spelling/script/names, never numbers) →
  `NativeChatEngine.interpret` (local intents) → `runFixed` in `MainActivity` executes against saved entries.
  Totals always come from the records, never from a model.
- Names: `Understand.matchContact` / `mostWordsMatched` / `closeName` (spelling + sound key in `Names.key`).
- Urdu/Hindi script → Roman: `Translit.toRoman`.
- Every fix to a chat mistake gets a unit test in `app/src/test/java/com/bolkhata/app/NativeChatEngineTest.kt`.

## Style
- Clean look: Inter font, off-white page, white cards, coloured line icons, red money out / green money in.
- Floating ChatGPT-style message bar (`AppParts.kt` `InputBar`); screens scroll under it, lists end with
  `barSpace()`.
- Match the surrounding code: short KDoc comments, no dead code, reuse helpers in `Ui.kt`.

## Token saving
- Read only the files the task needs; search with grep/rg first. Never read build/, .gradle/ or big logs.
- Don't re-read a file already read; don't scan the whole repo.
- Make only the requested change: no extra refactoring, files, docs or duplicate code; reuse existing helpers.
- Few tool calls, short terminal output (`head`, `-q`). No subagents for simple tasks.
- Replies: short, list only changed files and important errors, don't repeat earlier explanations.
- When compacting keep: current task, files changed, decisions, remaining work, build/test results.
