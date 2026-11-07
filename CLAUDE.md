# BolKhata (Smart Expense) — notes for Claude

Android khata/expense app, Kotlin + Jetpack Compose, single module `app/`. Package `com.bolkhata.app`.

## Talking to the owner
- Reply in short, simple Hinglish (Roman Urdu): 2-3 short lines max, the point first. Explanations too: 2-3 lines. No long explanations, tables or
  lists of every detail unless asked.
- Ask before every release. Never release without a clear "haan/release kar do" (or "build kar do").
- Once the owner said release/build: finish it. If the release build fails, fix it on the branch and release
  again without asking a second time; report only the final result (link) or what still can't be fixed.
- Screenshots/mock-ups only when asked; they are expensive. When the owner asks to see a design, show it as an
  HTML mock-up Artifact (quick, no build); use the screenshots workflow only if they ask for real screenshots.

## Build and release (no Android SDK in the cloud container)
- Code cannot be compiled locally. GitHub Actions (`.github/workflows/build.yml`) is the compiler and test runner.
- Work on the session branch. Do NOT run a separate test build before release: the release build runs
  `testReleaseUnitTest assembleRelease` and publishes nothing if anything fails.
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
- UI words: plain, common English everyone knows (Paid / Due, not "On credit"); one word where possible.
- Keep it simple: the owner asked to go back to 1.391 (Business chips with Production) after the Business/Personal
  redesign got too complex. Add new things only one small step at a time, mock first.
- When the owner changes the size/look of a common part (chips, search, business pill, cards), change it everywhere it
  appears (one shared composable), not just on the screen in the screenshot.

## Token saving
- Read only the files the task needs; search with grep/rg first. Never read build/, .gradle/ or big logs.
- Don't re-read a file already read; don't scan the whole repo.
- Make only the requested change: no extra refactoring, files, docs or duplicate code; reuse existing helpers.
- Few tool calls, short terminal output (`head`, `-q`). No subagents for simple tasks.
- Replies: short, list only changed files and important errors, don't repeat earlier explanations.
- When compacting keep: current task, files changed, decisions, remaining work, build/test results.
- Read only the lines needed (`grep -n`, `sed -n a,bp`); edit with small exact replacements, not whole-file rewrites.
- Batch related edits into one commit and one push; release only once per "release kar do".
- Build wait: one background loop on `git ls-remote --tags origin vN`, then one release lookup. No polling.
- Mock-ups: one small HTML file; for changes republish the same artifact with `edits`, never rebuild it.

## Quality (do it right the first time)
- Work like a careful senior designer-developer: the owner wants the best result, not the quickest. Think about
  the whole app, not one screen: every tab must share the same header, card, search, chips, list sheet, row
  size and colours. Before saying "done", look at the result (mock screenshot) and fix anything uneven yourself.
- Do exactly what the owner named, nothing more: "text size" means only font sizes, "background" only the
  background. If the ask is unclear, ask in one line before changing.
- Before pushing, check by eye: imports added, names exist (grep), no clashes, Compose opt-ins, string escapes.
- Logic changes (money, balances, parsing) get a small unit test; totals always from saved entries.
- Keep the look consistent: reuse KhataTabs.kt / Ui.kt helpers (KhataTop, SumCard, RegRow, Pills, colours).

## Fixing what the owner points at ("ye theek kar do")
- Think before doing: if an ask looks wrong or clashes with how the app works (e.g. "Parties like Cash" when the
  opened party khata already has those columns), say so plainly in one line and suggest the right place first.
- Get it right in one go: check fit, widths, colours and every affected screen yourself before showing, so the owner
  never has to ask for the same adjustment twice.
- Find the exact thing in the screenshot or comment (anchored element), change only that, then say in one line
  what changed. "Pehle jaisa" / "pichay jao" means undo the last step(s) exactly, nothing new.
- Design asks: change the mock-up first (same Artifact, `edits`), check its script with `node --check` and look at
  it once with a Playwright screenshot (columns fit, nothing overflows) before sending the link. After "ok/apply",
  copy the mock 1:1 into Compose (1 CSS px ≈ 1 sp/dp), commit, then ask "release kar doon?".
- Artifact comments arrive as notifications: read the thread, apply, reply in one Hinglish line, resolve it.
- A push to main is blocked unless the owner's latest words approve the release; if blocked, ask once, plainly.

## Look the owner likes (keep it)
- Mock-up of record: Artifact https://claude.ai/artifact/KaEp1kbD1q82ARWJdB9zhr (copied 1:1 into Compose).
- Page colour: light slate #E6E8EB by default; side menu Background offers light slate and warm grey (`PAGE_COLORS`,
  cards stay white), two dull dark looks (`DARK_LOOKS`: Graphite, Navy; each changes page, cards, wells, edges, day
  strip and field) and an own photo. Header dots: Clean, Red, Green, Navy. No glass on cards; only the app icon (`GlassLogo`) and the optional header band are glossy.
- Search box and chat bar (`fieldEdge`): as in the Claude app, white (dark: a shade lighter than the page), soft
  see-through edge, faint shadow, radius 16 (chat 19).
- App text is plain English only (account, not khata); no country-specific words (ID card, not Iqama). Chat answers in
  short English; people may still type in their own language.
- Report › Sales / Other income / Purchases / Expenses: a share bar (5 biggest in colours, rest grey) and rows with %,
  then the things inside (Expenses), then the entries. Long-press a business in the side menu: Rename / Delete.
- White business pill (`BusinessPill`: glossy logo + name, no arrow); tapping it opens the side menu, which is also
  Settings: businesses (+ Add business), Name, Currency, Language, Header dots, Background dots + photo, Backup, Trash,
  Version. No separate Settings page.
- Bottom tabs Chat · Cashbook · Party · Expense · Income (emoji icons 💬 📒 👥 🧾 💵). Chat bar only on Chat: a 38-high
  field on the page (no white strip, no +), 13sp, white round mic with a soft shadow.
  No Entry button on lists: entries are added inside a person or thing (solid + Paid / + Work, + Received / + Given, + Out).
- Party / Expense / Income = accounts (`Sides.kt`, `SideScreen`): rows with icon square, name, sub, amount + word, ›; dashed
  "+ Add account"; inner pages: round back button, path above title, one summary card, plain white ledger.
  Party holds every people account (Customers, Suppliers, Staff, People, own); Expense/Income hold things only, plus
  Purchases / Sales filled from the khatas (read-only). No Stock / Work views.
- Period chips (`PeriodChips`) on Party, Expense, Income and the Report: "All ▾" opens All, the years with entries and
  Dates… (from–to); then that year's months as "Nov 2026". Report: Sales, Other income, Purchases, Expenses, Profit, then
  Net worth (Cash in hand, To get, Stock, Assets, To pay, Net worth); 0 shows without a sign; Assets are not an expense.
- Cashbook ledger: head 13/10, rows 12/10, amounts 12 without + / −, columns 68, day strip #EEF1F8 with coloured
  day totals. Report rows 14/11/14, amount column 96, every row opens its detail; owed = To get / To pay only.
- Long-press an account or entry for Rename / Move / Delete. Red = money out, green = money in, orange = owed.
