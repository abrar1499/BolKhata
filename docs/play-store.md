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
- **Notification access:** used only to read bank alerts for suggested entries, and only after the user turns it on.

## Data safety form
- **Does the app collect or share user data?** Yes, but only through optional features the user turns on:
  - **Audio (voice recordings):** shared with Groq or ElevenLabs only when the user adds their own key. Purpose: app
    functionality. Not stored by the app developer. Optional.
  - **Messages / app activity (chat text):** sent to Groq only when the user adds their key. Purpose: app functionality.
    Optional.
- **Encrypted in transit:** Yes (HTTPS).
- **Can users delete their data?** Yes: in the app, or by uninstalling it.
- **Financial info:** stays on the device and is never collected or shared.

## Release steps
1. Create a Play Console account ($25) and verify your identity.
2. Create the app "Smart Expense" (Free, App).
3. Turn on Play App Signing (the default) and upload `SmartExpense-play.aab` to **Closed testing**.
4. Add 12 or more testers (by Gmail) who keep the app installed for 14 days.
5. Then apply for Production and send it for review.

Note: the Play version is signed by Google. A phone that has the GitHub APK installed must uninstall it once before
installing from Play. Back up first.
