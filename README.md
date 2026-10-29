# Smart Expense (Android)

Bolo aur hisaab khud apni jagah chala jaye. 🎤 dabao, bolo "Ahmed ko 200 riyal udhaar diye", entry ban jayegi.

## APK banane ka asaan tareeqa (bina Android Studio, sirf phone/computer browser)
1. github.com par free account banayein.
2. "New repository" banayein, naam `BolKhata`.
3. "uploading an existing file" par click karein aur is folder ki saari files (andar ke folders samet) drag karein. Commit karein.
   - Note: `.github` folder bhi upload hona chahiye. Agar drag se na jaye to computer se upload karein.
4. Sirf approved `[build-apk]` commit ya manual workflow dispatch se **Build APK** workflow chalta hai.
5. Green ✓ ke baad run ke **Artifacts** mein `BolKhata-apk` ZIP download karein; latest GitHub Release bhi check karein.
6. ZIP se `app-release.apk` phone par bhejein aur install karein ("Unknown sources" allow karna ho sakta hai).

Note: APK builds are triggered only after explicit user approval, not after ordinary changes.

## Android Studio se
Folder ko Android Studio mein "Open" karein, sync hone dein, phone USB se lagayein aur ▶ Run dabayein.

## Kaise kaam karta hai
- Awaaz: Google ki speech recognition (phone mein Google app hona chahiye). Upar "Roman / اردو" button se zabaan badlein.
- Samajhna: `Parser.kt` offline keywords se type, category, shakhs aur raqam nikalta hai.
- Data: sirf phone par save hota hai.
- Kisi entry par tap karein to raqam, shakhs, qism, category badal sakte hain ya mita sakte hain.

## Account safety and recovery
- Khata history mein **Select all** se multiple entries choose karke ek destination mein move kar sakte hain.
- Move dialog mein search aur new khata destination hai; move ke pehle confirmation hoti hai.
- Entry actions ya khata screen se **Move history & restore** khol sakte hain. Restore sirf tab hota hai jab entries baad mein kisi doosre khate mein move/edit nahi hui hon.
- Rename aur merge alag actions hain; existing khata ke saath merge karne se pehle affected entry count dikhaya jata hai.
- Chat/voice commands open khata ke context mein records filter karte hain; ambiguous multi-entry delete/move par clarification mangi jati hai.
- App interface English mein rehta hai. Voice recognition ki language alag setting hai; naye devices par language preference ka sensible default use hota hai.

Release workflow runs unit tests and builds a signed release APK. Install and test on your own phone before relying on it for important financial records.
