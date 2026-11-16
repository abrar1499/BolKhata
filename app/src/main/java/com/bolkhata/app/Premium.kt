package com.bolkhata.app

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Premium through Google Play: no ads and the smart assistant with no limits, $2 a month or $18 a year (Play Console ›
 * Monetize › Subscriptions: product "premium", base plans "monthly" and "yearly"; Play shows each country its own money).
 * Whether it is on is kept on the phone, so it holds without internet, and checked with Play each time the app opens.
 */
object Premium {
    const val PRODUCT = "premium"

    /** Premium is on: no ads, and no reward video before smart answers. */
    var active by mutableStateOf(false)
        private set

    /** The plans' prices as Google Play gives them in the person's money ("Rs 560"); "" until Play answered. */
    var monthly by mutableStateOf("")
        private set
    var yearly by mutableStateOf("")
        private set
    /** The yearly price for one month ("Rs 417"). */
    var yearlyMonth by mutableStateOf("")
        private set

    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    private fun saved(c: Context) = c.getSharedPreferences("premium", Context.MODE_PRIVATE)

    /** Connects to Google Play: the plans' prices, and whether this person has Premium. */
    fun start(activity: Activity) {
        val c = activity.applicationContext
        active = saved(c).getBoolean("on", false)
        if (client != null) return
        val billing = BillingClient.newBuilder(c)
            .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.let { took(c, it) } }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = billing
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                prices(billing)
                billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, list ->
                    if (r.responseCode == BillingClient.BillingResponseCode.OK) set(c, list.any { owns(it) })
                    list.filter { owns(it) && !it.isAcknowledged }.forEach { acknowledge(billing, it) }
                }
            }
            override fun onBillingServiceDisconnected() { client = null }
        })
    }

    private fun prices(billing: BillingClient) {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()
        billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { r, found ->
            val d = found.productDetailsList.firstOrNull()?.takeIf { r.responseCode == BillingClient.BillingResponseCode.OK } ?: return@queryProductDetailsAsync
            details = d
            plan(d, "monthly")?.let { monthly = it.formattedPrice }
            plan(d, "yearly")?.let { y ->
                yearly = y.formattedPrice
                yearlyMonth = runCatching {
                    java.text.NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(y.priceCurrencyCode) }
                        .format(y.priceAmountMicros / 12 / 1_000_000.0)
                }.getOrDefault("")
            }
        }
    }

    /** The first price of the base plan [id] ("monthly", "yearly"). */
    private fun plan(d: ProductDetails, id: String) = offer(d, id)?.pricingPhases?.pricingPhaseList?.firstOrNull()
    private fun offer(d: ProductDetails, id: String) = d.subscriptionOfferDetails?.filter { it.basePlanId == id }?.minByOrNull { if (it.offerId == null) 0 else 1 }

    /** Opens Google Play's payment sheet for the monthly or the [yearly] plan; false when Play has not given the plans. */
    fun buy(activity: Activity, yearly: Boolean): Boolean {
        val billing = client ?: return false
        val d = details ?: return false
        val o = offer(d, if (yearly) "yearly" else "monthly") ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(o.offerToken).build()
        return billing.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
            .responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun owns(p: Purchase) = PRODUCT in p.products && p.purchaseState == Purchase.PurchaseState.PURCHASED

    /** A purchase just made: Premium is on, and Play is told it was received (else Play gives the money back in 3 days). */
    private fun took(c: Context, purchases: List<Purchase>) {
        val got = purchases.filter { owns(it) }
        if (got.isEmpty()) return
        set(c, true)
        client?.let { b -> got.filter { !it.isAcknowledged }.forEach { acknowledge(b, it) } }
    }

    private fun acknowledge(b: BillingClient, p: Purchase) =
        b.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }

    private fun set(c: Context, on: Boolean) {
        active = on
        saved(c).edit().putBoolean("on", on).apply()
    }
}

/**
 * The Premium sheet, as the mock: a star, the name and one line, what it gives, the two plans (yearly picked, "Save
 * 25%"), one blue button with the price, and how to cancel. Once on, it says so and where to manage it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val blue = Color(0xFF2F6BE0)
    val blueSoft = Color(0xFFEAF0FD)
    val sub = Color(0xFF8A8580)
    val line = Color(0xFFE2DED8)
    var yearly by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalPage.current, dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.5.dp, blue.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) { Text("⭐", fontSize = 22.sp) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Text("✕", fontSize = 12.sp) }
            }
            Column {
                Text("Smart Expense Premium", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your accounts, without ads or limits.", fontSize = 12.5.sp, color = sub)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Give("🚫", "No ads", "Not in the chat, the cashbook or any list", blueSoft, sub)
                HorizontalDivider(color = line)
                Give("✦", "Smart assistant, no limits", "Ask as much as you like, no videos", blueSoft, sub)
            }
            if (Premium.active) {
                Text("✓ Premium is on", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GreenDeep, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Manage or cancel it in Google Play › Subscriptions", fontSize = 12.sp, color = blue, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Premium.PRODUCT}&package=${context.packageName}")))
                        }
                    })
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanCard("Monthly", Premium.monthly, "a month", null, !yearly, blue, line, sub, Modifier.weight(1f).fillMaxHeight()) { yearly = false }
                    PlanCard("Yearly", Premium.yearly, "a year" + if (Premium.yearlyMonth.isBlank()) "" else " · ${Premium.yearlyMonth} a month", "Save 25%",
                        yearly, blue, line, sub, Modifier.weight(1f).fillMaxHeight()) { yearly = true }
                }
                val price = if (yearly) Premium.yearly else Premium.monthly
                val ready = price.isNotBlank()
                Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(blue).alpha(if (ready) 1f else 0.4f)
                    .clickable(enabled = ready) { (context as? Activity)?.let { Premium.buy(it, yearly) } }, contentAlignment = Alignment.Center) {
                    Text(if (ready) "Continue · $price " + (if (yearly) "a year" else "a month") else "Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                if (!BuildConfig.PLAY) Text("Premium is bought in the Smart Expense app from Google Play.", fontSize = 11.sp, color = sub,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Text("Paid through Google Play. Renews by itself; cancel any time in Google Play › Subscriptions.", fontSize = 10.5.sp, lineHeight = 14.sp,
                color = sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One thing Premium gives: a round soft-blue mark, the name and a line. */
@Composable
private fun Give(mark: String, title: String, line: String, ring: Color, sub: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) { Text(mark, fontSize = 14.sp) }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(line, fontSize = 11.5.sp, color = sub)
        }
    }
}

/** A plan to pick: its name, Play's price ("…" until it comes), a line, and a small blue [tag] above its corner. */
@Composable
private fun PlanCard(name: String, price: String, per: String, tag: String?, on: Boolean, blue: Color, line: Color, sub: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.padding(top = 9.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.5.dp, if (on) blue else line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = sub)
            Text(price.ifBlank { "…" }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
            Text(per, fontSize = 11.sp, color = sub)
        }
        if (tag != null) Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = (-9).dp).clip(RoundedCornerShape(8.dp)).background(blue).padding(horizontal = 7.dp, vertical = 2.dp))
    }
}
