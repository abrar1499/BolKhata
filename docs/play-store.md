# Google Play listing: Smart Expense

## Files
- App bundle: `SmartExpense-play.aab` on every GitHub release. It has no self-updater, because Play installs the updates.
- Privacy policy: `https://abrar1499.github.io/BolKhata/privacy.html`. Turn this on in GitHub: Settings → Pages →
  Branch `main`, folder `/docs`.
- Screenshots: `docs/screenshots/`. Play needs at least 2 phone screenshots.
- Icon: 512×512 PNG. Feature graphic: 1024×500 PNG.

## Store listing
- **App name:** Smart Expense: Khata & Expense
- **Short description** (80 characters or fewer): Speak or type your expenses in Urdu, Hindi or English. Khata, cashbook and reports.
- **Full description:**

  Smart Expense is a simple khata and expense book that understands how you talk.

  • Say "chai 5" or "Hamid ko 500 diye", and the entry is saved.
  • Party khata: payable and receivable for every party at a glance.
  • Cashbook, expense and income by month, with day totals.
  • Ask questions such as "is mahine food kitna" or "19 aug se aaj tak kharcha".
  • Business: stock, sale, purchase and profit.
  • Import bank PDF statements and receipts. They are read on your phone.
  • Reports with charts, plus backup and restore.

  Your data stays on your phone. There is no account and no ads.

- **Category:** Finance
- **Contact email:** abrarlive14@gmail.com

## App content (Play Console → Policy → App content)
- **Ads:** No.
- **Target audience:** 18 and over.
- **Content rating questionnaire:** Utility / productivity. Answer "No" to every content question.
- **Financial features:** none of loans, banking, crypto or trading. It is only a personal expense tracker.
- **Notification access:** not used. The Play build leaves out bank alerts, chat review, ElevenLabs voice and the self-updater.

## Data safety form
- **Does the app collect or share user data?** No. Everything stays on the phone (Groq AI, voice calls and bank
  alerts are switched off in the app for now). If they come back, update this form and the privacy policy first.
- **Encrypted in transit:** not applicable (nothing is sent).
- **Can users delete their data?** Yes: in the app, or by uninstalling it.

## Things that can get the account in trouble (keep it this way)
- Upload only `SmartExpense-play.aab` (no self-updater, no notification reading). Never the GitHub APK.
- Keep the privacy policy and the Data safety form matching what the app really sends (update both when that changes).
- No other brands' names or logos in the listing (ChatGPT, Claude, banks); no fake reviews; no misleading claims.
- Name, address and contact details in the console must stay true; one developer account only; no VPN tricks.
- Answer every policy email from Google within its deadline.

## Release steps
1. Create a Play Console account ($25) and verify your identity.
2. Create the app "Smart Expense" (Free, App).
3. Turn on Play App Signing (the default) and upload `SmartExpense-play.aab` to **Closed testing**.
4. Add 12 or more testers (by Gmail) who keep the app installed for 14 days.
5. Then apply for Production and send it for review.

Note: the Play version is signed by Google. A phone that has the GitHub APK installed must uninstall it once before
installing from Play. Back up first.
