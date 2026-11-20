package com.bolkhata.app

import android.app.Activity
import android.content.Context
import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Most new ads one phone is shown in a day (chat, cashbook, lists); after that their places stay empty until the next day. */
internal const val AD_LOADS_A_DAY = 40

/** Most reward videos one phone is offered in a day; after that the app answers by itself until the next day. */
internal const val REWARDS_A_DAY = 5

/** Most taps on ads one phone may make in a day; after that its ads rest until the next day. */
internal const val AD_TAPS_A_DAY = 3

/** The taps on ads counted so far on [day], from what was saved ([savedDay], [saved]): a new day starts at none. */
internal fun adTapsOn(day: String, savedDay: String, saved: Int): Int = if (savedDay == day) saved else 0

/**
 * Quiet ads from Google AdMob: a small native card that looks like the app (an answer in the chat, a row under the
 * accounts, a slim one at the end of a list, a big one under a short list). Each keeps its small "Ad" tag. The APK the owner
 * installs shows Google's test ads (so the account is never hurt by the owner's own taps); the Play build the real ones.
 */
object Ads {
    private val play = BuildConfig.PLAY
    val nativeUnit = if (play) "ca-app-pub-2130743939844876/8559275509" else "ca-app-pub-3940256099942544/2247696110"
    /** Pictures only (no video), for the slim row between accounts: a video would need the tall card there. */
    val smallUnit = if (play) "ca-app-pub-2130743939844876/4019539897" else "ca-app-pub-3940256099942544/2247696110"

    /** The short video watched for more smart answers (AdMob "Chat AI answers", reward 10 Smart answers). */
    val rewardUnit = if (play) "ca-app-pub-2130743939844876/5436011037" else "ca-app-pub-3940256099942544/5224354917"
    private var reward: RewardedAd? = null
    private var rewardLoading = false

    /** Fetches the next reward video ahead of time, so it starts at once when asked for. */
    fun loadReward(context: Context) {
        if (!ready || reward != null || rewardLoading) return
        rewardLoading = true
        RewardedAd.load(context, rewardUnit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { reward = ad; rewardLoading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { rewardLoading = false }
        })
    }

    /**
     * Shows the reward video; [done] gets whether it was watched to the end. With no video to show (none came, or ads
     * are not allowed here) it is true at once: the person is never stopped by a missing ad.
     */
    fun showReward(activity: Activity, done: (Boolean) -> Unit) {
        val ad = reward ?: run { loadReward(activity); done(true); return }
        reward = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { done(earned); loadReward(activity) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { done(true); loadReward(activity) }
        }
        ad.show(activity) { earned = true; count(activity, "rewards") }
    }

    /** Loaded native ads by their place (e.g. "chat-12"), so an ad stays the same while it is on screen and is not fetched again. */
    /** Ads fetched, by place; read in Compose, so a list redraws its card when the ad inside it arrives. */
    private val loaded = mutableStateMapOf<String, NativeAd>()
    /** When each place's ad came, and when it was last asked for. */
    private val loadedAt = mutableMapOf<String, Long>()
    private val asking = mutableMapOf<String, Long>()
    /** The ad in hand for [place], if any. */
    fun ad(place: String): NativeAd? = loaded[place]
    /** Whether the ad for [place] is in hand and may be shown now. */
    fun showing(place: String) = loaded[place] != null && !resting && !Premium.active

    /** Ads may load: Google said yes after the consent form, where one is needed. Drawn ads wait for it. */
    var ready by mutableStateOf(false)
        private set

    /**
     * The day's limits (taps, loads, reward videos) are for the Play app only: the test APK shows Google's test ads
     * without a limit, to check them as often as needed.
     *
     * True once this phone tapped ads [AD_TAPS_A_DAY] times today: no ad is drawn or fetched until tomorrow. It keeps the
     * account safe from someone tapping the ads again and again on purpose; a real reader never gets that far.
     */
    val resting: Boolean get() = play && restDay == today()

    /** The day the taps ran out; the next day the ads are back by themselves, also when the app was left open overnight. */
    private var restDay by mutableStateOf("")

    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    private fun saved(c: Context) = c.getSharedPreferences("ads", Context.MODE_PRIVATE)
    private fun tapsToday(c: Context) = saved(c).let { adTapsOn(today(), it.getString("day", "").orEmpty(), it.getInt("taps", 0)) }

    /** How many of [what] ("loads", "rewards") came today, from what was saved (a new day starts at none). */
    private fun today(c: Context, what: String) = saved(c).let { adTapsOn(today(), it.getString("$what-day", "").orEmpty(), it.getInt(what, 0)) }
    private fun count(c: Context, what: String) = saved(c.applicationContext).edit().putString("$what-day", today()).putInt(what, today(c, what) + 1).apply()

    /** Whether a reward video may still be offered today (at most [REWARDS_A_DAY]). */
    fun rewardsLeft(c: Context) = !play || today(c, "rewards") < REWARDS_A_DAY

    /** Counts each tap on the ad it is given to. */
    fun taps(context: Context): AdListener = object : AdListener() {
        override fun onAdClicked() {
            val c = context.applicationContext
            val n = tapsToday(c) + 1
            saved(c).edit().putString("day", today()).putInt("taps", n).apply()
            if (n >= AD_TAPS_A_DAY) restDay = today()
        }
    }

    /** Asks for consent where the law needs it (Google's form, set in AdMob › Privacy & messaging), then starts the ads. */
    fun start(activity: Activity) {
        restDay = if (tapsToday(activity) >= AD_TAPS_A_DAY) today() else ""
        val info = UserMessagingPlatform.getConsentInformation(activity)
        fun go() { if (info.canRequestAds() && !ready) { MobileAds.initialize(activity) {}; ready = true } }
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { go() } }, { go() })
        go()
    }

    /** The ad for [place], fetched once; [onReady] gets it when it arrives. [small]: from the pictures-only unit. */
    fun native(context: Context, place: String, small: Boolean = false, onReady: (NativeAd) -> Unit) {
        val now = System.currentTimeMillis()
        // Google wants an ad shown within the hour it came: an older one stays on show while a fresh one is asked for.
        loaded[place]?.let { onReady(it); if (now - (loadedAt[place] ?: 0L) < 3_600_000L) return }
        // One request at a time for a place (a list asks ahead, its card asks when drawn); a failed one is tried again after 30 s.
        if (now - (asking[place] ?: 0L) < 30_000L) return
        if (!ready || resting || Premium.active || (play && today(context, "loads") >= AD_LOADS_A_DAY)) return
        asking[place] = now
        AdLoader.Builder(context, if (small) smallUnit else nativeUnit)
            .forNativeAd { ad -> loaded[place] = ad; loadedAt[place] = System.currentTimeMillis(); asking.remove(place); count(context, "loads"); onReady(ad) }
            .withAdListener(taps(context))
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build()).build())
            .build().loadAd(AdRequest.Builder().build())
    }
}

/** Ads between rows take turns, counted from 1: a video card first, then a slim picture, and so on. */
fun videoTurn(nth: Int) = nth % 2 == 1

/**
 * A native ad as one soft row: its small picture, headline with its "Ad" tag, a short line, and the green action word;
 * nothing when no ad came; [round] false gives it square corners, like the cashbook sheet. [video], or an ad that is a video: a 120 × 120 picture or video (playing without sound) on
 * the left instead (Google asks for the ad's picture in its own MediaView, and a video no smaller than that). [small]:
 * an ad of pictures only, so the row stays slim between accounts. [big]: the white card under a short list, its picture
 * or video wider (192 × 120). [banner]: the strip at the end of the Cashbook and the Report. [corner]: its corner
 * radius in dp, as the chat's bubbles (8).
 */
@Composable
fun NativeAdCard(place: String, modifier: Modifier = Modifier, video: Boolean = false, round: Boolean = true, small: Boolean = false, big: Boolean = false, banner: Boolean = false, corner: Int? = null) {
    val context = LocalContext.current
    DisposableEffect(place, Ads.ready) {
        Ads.native(context, place, small) {}
        onDispose { }
    }
    val shown = Ads.ad(place)?.takeIf { !Ads.resting && !Premium.active } ?: return
    AndroidView(factory = { ctx: Context -> (if (banner) bannerRow(ctx) else if (big) videoRow(ctx, wide = true) else if (video || shown.mediaContent?.hasVideoContent() == true) videoRow(ctx) else adRow(ctx)).also { v -> (v.getChildAt(0).background as? GradientDrawable)?.let { if (!round) { it.cornerRadius = 0f; it.setColor(AColor.argb(217, 255, 255, 255)) } else if (corner != null) it.cornerRadius = ctx.dp(corner).toFloat() } } }, modifier = modifier.fillMaxWidth(), update = { v: NativeAdView -> v.show(shown) })
}

private fun Context.dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** The native ad's own view, drawn like the mock: a soft white rounded row, its picture 64 × 36, two lines, green action. */
private fun adRow(ctx: Context): NativeAdView {
    val view = NativeAdView(ctx)
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(56)
        setPadding(ctx.dp(10), ctx.dp(7), ctx.dp(10), ctx.dp(7))
        background = GradientDrawable().apply { cornerRadius = ctx.dp(12).toFloat(); setColor(AColor.argb(153, 255, 255, 255)) }
    }
    val media = MediaView(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(64), ctx.dp(36)); clipToOutline = true
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        background = GradientDrawable().apply { cornerRadius = ctx.dp(8).toFloat(); setColor(AColor.rgb(238, 247, 241)) } }
    val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(9); marginEnd = ctx.dp(8) } }
    val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val headline = TextView(ctx).apply { textSize = 11.5f; setTextColor(AColor.rgb(29, 29, 31)); maxLines = 1; paint.isFakeBoldText = true }
    val tag = TextView(ctx).apply { text = "Ad"; textSize = 8f; setTextColor(AColor.rgb(155, 149, 142)); setPadding(ctx.dp(3), 0, ctx.dp(3), 0)
        minHeight = ctx.dp(15); minWidth = ctx.dp(15); gravity = Gravity.CENTER  // Google: the ad badge at least 15 high and wide
        background = GradientDrawable().apply { cornerRadius = ctx.dp(4).toFloat(); setStroke(ctx.dp(1), AColor.rgb(214, 209, 202)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = ctx.dp(5) } }
    val body = TextView(ctx).apply { textSize = 10f; setTextColor(AColor.rgb(138, 133, 128)); maxLines = 1 }
    val action = TextView(ctx).apply { textSize = 10.5f; setTextColor(AColor.rgb(31, 154, 84)); paint.isFakeBoldText = true }
    titleRow.addView(headline); titleRow.addView(tag)
    texts.addView(titleRow); texts.addView(body)
    row.addView(media); row.addView(texts); row.addView(action)
    view.addView(row)
    view.mediaView = media; view.headlineView = headline; view.bodyView = body; view.callToActionView = action
    return view
}

/**
 * The video card: the ad's picture or video flush on the left, 120 high (Google's smallest for video), 120 wide or 192
 * when [wide]; beside it the headline with its "Ad" tag, two lines and a green action button.
 */
private fun videoRow(ctx: Context, wide: Boolean = false): NativeAdView {
    val view = NativeAdView(ctx)
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, ctx.dp(10), 0)
        background = GradientDrawable().apply { cornerRadius = ctx.dp(if (wide) 14 else 12).toFloat(); setColor(if (wide) AColor.WHITE else AColor.argb(179, 255, 255, 255)) }
        clipToOutline = true
    }
    val media = MediaView(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(if (wide) 192 else 120), ctx.dp(120))
        setImageScaleType(ImageView.ScaleType.CENTER_CROP); setBackgroundColor(AColor.rgb(238, 247, 241)) }
    val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(10) } }
    val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val headline = TextView(ctx).apply { textSize = 12f; setTextColor(AColor.rgb(29, 29, 31)); maxLines = 2; paint.isFakeBoldText = true
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
    val tag = TextView(ctx).apply { text = "Ad"; textSize = 8f; setTextColor(AColor.rgb(155, 149, 142)); setPadding(ctx.dp(3), 0, ctx.dp(3), 0)
        minHeight = ctx.dp(15); minWidth = ctx.dp(15); gravity = Gravity.CENTER  // Google: the ad badge at least 15 high and wide
        background = GradientDrawable().apply { cornerRadius = ctx.dp(4).toFloat(); setStroke(ctx.dp(1), AColor.rgb(214, 209, 202)) } }
    val body = TextView(ctx).apply { textSize = 10.5f; setTextColor(AColor.rgb(138, 133, 128)); maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, ctx.dp(3), 0, 0) }
    val action = TextView(ctx).apply { textSize = 11f; setTextColor(AColor.WHITE); paint.isFakeBoldText = true
        setPadding(ctx.dp(11), ctx.dp(6), ctx.dp(11), ctx.dp(6))
        background = GradientDrawable().apply { cornerRadius = ctx.dp(10).toFloat(); setColor(AColor.rgb(31, 154, 84)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(8) } }
    titleRow.addView(headline); titleRow.addView(tag)
    texts.addView(titleRow); texts.addView(body); texts.addView(action)
    row.addView(media); row.addView(texts)
    view.addView(row)
    view.mediaView = media; view.headlineView = headline; view.bodyView = body; view.callToActionView = action
    return view
}

/**
 * The banner strip, as the mock: white, square, 56 high; the ad's picture 38 × 38 (rounded 8), headline with its "Ad" tag
 * and a short line, a blue "OPEN" button; Google puts its AdChoices mark in the top right corner.
 */
private fun bannerRow(ctx: Context): NativeAdView {
    val view = NativeAdView(ctx).apply { tag = "banner" }
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(56)
        setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
        setBackgroundColor(AColor.WHITE)
    }
    val media = MediaView(ctx).apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(38), ctx.dp(38)); clipToOutline = true
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        background = GradientDrawable().apply { cornerRadius = ctx.dp(8).toFloat(); setColor(AColor.rgb(238, 247, 241)) } }
    val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(10); marginEnd = ctx.dp(10) } }
    val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val headline = TextView(ctx).apply { textSize = 12f; setTextColor(AColor.rgb(29, 29, 31)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; paint.isFakeBoldText = true }
    val tag = TextView(ctx).apply { text = "Ad"; textSize = 8f; setTextColor(AColor.rgb(155, 149, 142)); setPadding(ctx.dp(3), 0, ctx.dp(3), 0)
        minHeight = ctx.dp(15); minWidth = ctx.dp(15); gravity = Gravity.CENTER  // Google: the ad badge at least 15 high and wide
        background = GradientDrawable().apply { cornerRadius = ctx.dp(4).toFloat(); setStroke(ctx.dp(1), AColor.rgb(214, 209, 202)) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = ctx.dp(5) } }
    val body = TextView(ctx).apply { textSize = 10f; setTextColor(AColor.rgb(138, 133, 128)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    val action = TextView(ctx).apply { textSize = 11f; setTextColor(AColor.WHITE); paint.isFakeBoldText = true
        setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
        background = GradientDrawable().apply { cornerRadius = ctx.dp(4).toFloat(); setColor(AColor.rgb(26, 115, 232)) } }
    titleRow.addView(headline); titleRow.addView(tag)
    texts.addView(titleRow); texts.addView(body)
    row.addView(media); row.addView(texts); row.addView(action)
    view.addView(row)
    view.mediaView = media; view.headlineView = headline; view.bodyView = body; view.callToActionView = action
    return view
}

/** Fills the row with [ad] and hands it to Google, which counts its showing and opens it on a tap. */
private fun NativeAdView.show(ad: NativeAd) {
    (headlineView as TextView).text = ad.headline
    (bodyView as TextView).text = ad.body.orEmpty()
    (callToActionView as TextView).text = (ad.callToAction ?: "Open").let { if (tag == "banner") it.uppercase() else "$it ›" }
    (iconView as? ImageView)?.let { it.setImageDrawable(ad.icon?.drawable); it.visibility = if (ad.icon == null) android.view.View.GONE else android.view.View.VISIBLE }
    ad.mediaContent?.let { mediaView?.mediaContent = it }
    setNativeAd(ad)
}
