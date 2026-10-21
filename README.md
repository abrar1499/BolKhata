# Bol Khata (Android)

Bolo aur hisaab khud apni jagah chala jaye. 🎤 dabao, bolo "Ahmed ko 200 riyal udhaar diye", entry ban jayegi.

## APK banane ka asaan tareeqa (bina Android Studio, sirf phone/computer browser)
1. github.com par free account banayein.
2. "New repository" banayein, naam `BolKhata`.
3. "uploading an existing file" par click karein aur is folder ki saari files (andar ke folders samet) drag karein. Commit karein.
   - Note: `.github` folder bhi upload hona chahiye. Agar drag se na jaye to computer se upload karein.
4. Repository mein **Actions** tab kholein. "Build APK" chal raha hoga (5-8 minute).
5. Hara ✓ aane par us run ko kholein, neeche **Artifacts** mein `BolKhata-apk` download karein.
6. Zip kholein, `app-debug.apk` phone par bhejein aur install karein ("Unknown sources" allow karna hoga).

## Android Studio se
Folder ko Android Studio mein "Open" karein, sync hone dein, phone USB se lagayein aur ▶ Run dabayein.

## Kaise kaam karta hai
- Awaaz: Google ki speech recognition (phone mein Google app hona chahiye). Upar "Roman / اردو" button se zabaan badlein.
- Samajhna: `Parser.kt` offline keywords se type, category, shakhs aur raqam nikalta hai.
- Data: sirf phone par save hota hai.
- Kisi entry par tap karein to raqam, shakhs, qism, category badal sakte hain ya mita sakte hain.
