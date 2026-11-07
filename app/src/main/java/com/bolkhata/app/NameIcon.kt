package com.bolkhata.app

/**
 * Picks a picture for an account or item from its name, in English or Roman Urdu ("doodh" 🥛, "gym" 🏋️, "kiraya" 🏠).
 * A word of the name must start with a key (short keys: the whole word), so "car" never matches "carpet". Nothing fits: 💸 for money out, 💵 for in.
 */
object NameIcon {
    private val table: List<Pair<String, String>> = listOf(
        // Food and drink
        "grocer" to "🛒", "kiryana" to "🛒", "karyana" to "🛒", "mart" to "🛒", "supermarket" to "🛒", "store" to "🏪", "dukan" to "🏪", "shop" to "🛍️",
        "restaurant" to "🍽️", "hotel" to "🍽️", "dhaba" to "🍽️", "dinner" to "🍽️", "lunch" to "🍱", "breakfast" to "🍳", "nashta" to "🍳", "khana" to "🍛",
        "food" to "🍛", "meal" to "🍛", "tea" to "☕", "chai" to "☕", "coffee" to "☕", "snack" to "🍿", "biscuit" to "🍪", "sweet" to "🍬", "mithai" to "🍬",
        "bread" to "🍞", "roti" to "🫓", "naan" to "🫓", "bakery" to "🥐", "cake" to "🎂", "milk" to "🥛", "doodh" to "🥛", "dairy" to "🥛", "dahi" to "🥛",
        "yogurt" to "🥛", "egg" to "🥚", "anday" to "🥚", "anda" to "🥚", "meat" to "🥩", "gosht" to "🥩", "beef" to "🥩", "mutton" to "🥩", "chicken" to "🍗",
        "murgh" to "🍗", "fish" to "🐟", "machli" to "🐟", "fruit" to "🍎", "phal" to "🍎", "apple" to "🍎", "banana" to "🍌", "kela" to "🍌", "mango" to "🥭",
        "aam" to "🥭", "vegetable" to "🥦", "veg" to "🥦", "sabzi" to "🥦", "sabji" to "🥦", "potato" to "🥔", "aloo" to "🥔", "onion" to "🧅", "pyaz" to "🧅",
        "tomato" to "🍅", "tamatar" to "🍅", "rice" to "🍚", "chawal" to "🍚", "flour" to "🌾", "atta" to "🌾", "aata" to "🌾", "sugar" to "🍬", "cheeni" to "🍬",
        "oil" to "🫗", "ghee" to "🫗", "spice" to "🌶️", "masala" to "🌶️", "salt" to "🧂", "namak" to "🧂", "water" to "💧", "pani" to "💧", "juice" to "🧃",
        "drink" to "🥤", "cold" to "🥤", "pepsi" to "🥤", "ice" to "🍦", "pizza" to "🍕", "burger" to "🍔", "shawarma" to "🌯", "biryani" to "🍛",
        // Transport
        "petrol" to "⛽", "diesel" to "⛽", "fuel" to "⛽", "cng" to "⛽", "car" to "🚗", "gari" to "🚗", "gaari" to "🚗", "bike" to "🏍️", "motorcycle" to "🏍️",
        "cycle" to "🚲", "repair" to "🔧", "mechanic" to "🔧", "service" to "🔧", "tyre" to "🛞", "tire" to "🛞", "wash" to "🧽", "parking" to "🅿️",
        "toll" to "🛣️", "taxi" to "🚕", "uber" to "🚕", "careem" to "🚕", "indrive" to "🚕", "rickshaw" to "🛺", "bus" to "🚌", "train" to "🚆", "flight" to "✈️",
        "ticket" to "🎫", "travel" to "🧳", "safar" to "🧳", "transport" to "🚚", "truck" to "🚚", "tractor" to "🚜", "freight" to "🚚", "kiraya" to "🏠",
        // Home and bills
        "rent" to "🏠", "house" to "🏠", "home" to "🏠", "ghar" to "🏠", "makan" to "🏠", "flat" to "🏢", "room" to "🛏️", "furniture" to "🛋️",
        "repairs" to "🔨", "plumber" to "🔧", "paint" to "🎨", "household" to "🧺", "cleaning" to "🧹", "maid" to "🧹", "laundry" to "🧺", "dhobi" to "🧺",
        "electric" to "💡", "bijli" to "💡", "light" to "💡", "gas" to "🔥", "sui" to "🔥", "cylinder" to "🔥", "water bill" to "💧", "bill" to "🧾",
        "internet" to "🌐", "wifi" to "📶", "ptcl" to "🌐", "mobile" to "📱", "phone" to "📱", "recharge" to "📱", "load" to "📱", "balance" to "📱",
        "tv" to "📺", "cable" to "📺", "netflix" to "📺", "subscription" to "🔁",
        // Health and care
        "doctor" to "🩺", "clinic" to "🩺", "hospital" to "🏥", "medicine" to "💊", "dawai" to "💊", "dawa" to "💊", "pharmacy" to "💊", "medical" to "💊",
        "health" to "💊", "test" to "🧪", "lab" to "🧪", "dentist" to "🦷", "gym" to "🏋️", "fitness" to "🏋️", "haircut" to "💈", "barber" to "💈", "salon" to "💈",
        "personal" to "💈", "soap" to "🧼", "sabun" to "🧼", "shampoo" to "🧴", "cosmetic" to "💄", "makeup" to "💄", "perfume" to "🌸",
        // Family, education, giving
        "school" to "🏫", "college" to "🎓", "university" to "🎓", "fee" to "🧾", "fees" to "🧾", "tuition" to "📚", "education" to "📚", "book" to "📚",
        "kitab" to "📚", "stationery" to "✏️", "kids" to "🧒", "bachay" to "🧒", "baby" to "🍼", "toy" to "🧸", "family" to "👨‍👩‍👧", "wife" to "👩",
        "mother" to "👩", "ammi" to "👩", "father" to "👨", "abbu" to "👨", "sent home" to "💸", "remittance" to "💸", "gift" to "🎁", "tohfa" to "🎁",
        "charity" to "🤲", "sadqa" to "🤲", "zakat" to "🤲", "khairat" to "🤲", "donation" to "🤲", "mosque" to "🕌", "masjid" to "🕌", "wedding" to "💍",
        "shadi" to "💍", "party" to "🎉", "eid" to "🌙", "qurbani" to "🐐",
        // Shopping and things
        "shopping" to "🛍️", "cloth" to "👕", "kapre" to "👕", "kapray" to "👕", "dress" to "👗", "shoe" to "👟", "joote" to "👟", "bag" to "👜", "watch" to "⌚",
        "electronic" to "🔌", "charger" to "🔌", "laptop" to "💻", "computer" to "💻", "printer" to "🖨️", "camera" to "📷", "gold" to "🪙", "jewel" to "💍",
        "amazon" to "📦", "online" to "📦", "parcel" to "📦", "courier" to "📦", "pet" to "🐾", "cat" to "🐈", "dog" to "🐕", "plant" to "🪴", "garden" to "🪴",
        // Government, bank, office
        "govt" to "🏛️", "government" to "🏛️", "iqama" to "🪪", "visa" to "🛂", "passport" to "🛂", "fine" to "🚨", "challan" to "🚨", "tax" to "🏛️",
        "bank" to "🏦", "atm" to "🏧", "card" to "💳", "loan" to "🤝", "qarz" to "🤝", "udhar" to "🤝", "insurance" to "🛡️", "office" to "🏢",
        "stationary" to "✏️", "internet bill" to "🌐", "software" to "💻", "ads" to "📣", "advert" to "📣", "marketing" to "📣",
        // Business and production
        "production" to "🏭", "factory" to "🏭", "bhatta" to "🧱", "kiln" to "🧱", "eint" to "🧱", "int" to "🧱", "brick" to "🧱", "kachi" to "🧱",
        "pakki" to "🧱", "moulding" to "🧱", "thapai" to "🧱", "bharai" to "🚚", "loading" to "🚚", "unloading" to "📤", "nikasi" to "📤", "nakasi" to "📤",
        "labour" to "👷", "labor" to "👷", "mazdoor" to "👷", "worker" to "👷", "karigar" to "👷", "wage" to "👷", "dihari" to "👷", "raw" to "🧱",
        "material" to "🧱", "coal" to "⚫", "koila" to "⚫", "wood" to "🪵", "lakri" to "🪵", "cement" to "🏗️", "sand" to "⛱️", "ret" to "⛱️", "bajri" to "🪨",
        "steel" to "🔩", "sariya" to "🔩", "iron" to "🔩", "mitti" to "⛰️", "soil" to "⛰️", "clay" to "⛰️", "machine" to "⚙️", "tools" to "🛠️",
        "generator" to "⚡", "stock" to "📦", "maal" to "📦", "goods" to "📦", "purchase" to "📦", "supplier" to "🚚", "staff" to "👷", "employee" to "👷",
        "driver" to "🚚", "guard" to "💂", "munshi" to "📒", "accountant" to "📒",
        // Money in
        "salary" to "💼", "tankhwah" to "💼", "job" to "💼", "pay" to "💼", "business" to "🏪", "sale" to "🧾", "sales" to "🧾", "customer" to "👤",
        "profit" to "📈", "commission" to "💹", "bonus" to "🎉", "rent received" to "🏘️", "rental" to "🏘️", "interest" to "🏦", "freelance" to "💻",
        "income" to "💰", "other income" to "💰", "refund" to "↩️", "cashback" to "↩️",
        // Seen in real books: wallets, buy-now-pay-later, visas, fines
        "wallet" to "👛", "barq" to "👛", "easypaisa" to "👛", "jazzcash" to "👛", "stcpay" to "👛", "tamara" to "💳", "tabby" to "💳",
        "transfer" to "🔁", "metro" to "🚇", "exit" to "🛂", "reentry" to "🛂", "traffic" to "🚨", "diploma" to "🎓", "council" to "🏛️",
        "voucher" to "📱", "zain" to "📱", "stc" to "📱", "mobily" to "📱", "jazz" to "📱", "telenor" to "📱", "zong" to "📱", "ufone" to "📱",
        "laundries" to "🧺", "sweets" to "🍬", "haircut" to "💈", "air" to "✈️",
        "asset" to "🏷️", "contract" to "🏗️", "thaper" to "🧱", "pathera" to "🧱"
    )

    /** The picture for [name]; [income] picks the fallback when no word fits. */
    fun of(name: String, income: Boolean = false): String {
        val n = name.lowercase().trim()
        val words = n.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        // A whole phrase first ("water bill"), then any word that starts with a key.
        table.firstOrNull { (k, _) -> k.contains(' ') && n.contains(k) }?.let { return it.second }
        // The longest key wins ("loading" over "load"); short keys (car, tea, bus) must be the whole word.
        return table.filter { (k, _) -> !k.contains(' ') && words.any { w -> w == k || (k.length >= 4 && w.startsWith(k)) } }
            .maxByOrNull { it.first.length }?.second ?: if (income) "💵" else "💸"
    }
}
