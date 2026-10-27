package com.example

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.io.File

class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "AdMobManager"
    const val ADMOB_APP_ID = "ca-app-pub-7870922899333278~7654025008"
    const val ADMOB_BANNER_ID = "ca-app-pub-7870922899333278/7638335254"
    const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-7870922899333278/9796052747"
    const val ADMOB_NATIVE_ID = "ca-app-pub-7870922899333278/4911693049"
  }

  private var interstitialAd: InterstitialAd? = null
  private var isInterstitialLoading: Boolean = false

  // Reactive state for UI
  private var nativeAdState by mutableStateOf<NativeAd?>(null)
  private var currentScreenState by mutableStateOf("screen-home")
  private var showNativeAdCard by mutableStateOf(true)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    cleanupCorruptedWebViewVariationsSeed()
    enableEdgeToEdge()

    // 1. Initialize Google Mobile Ads SDK
    MobileAds.initialize(this) { initializationStatus ->
      Log.d(TAG, "Google MobileAds initialized: $initializationStatus")
      // Pre-load Interstitial and Native ads
      loadInterstitialAd()
      loadNativeAd()
    }

    setContent {
      MyApplicationTheme {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF070A13))
            .safeDrawingPadding()
            .testTag("game_root_container")
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            // Main Web Game View
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
              TicTacToeGameScreen(
                onGameOver = {
                  runOnUiThread { showInterstitialAd() }
                },
                onScreenChanged = { screen ->
                  runOnUiThread {
                    currentScreenState = screen
                    if (isHomeScreen(screen)) {
                      showNativeAdCard = true
                    }
                  }
                }
              )

              // Native Ad: Show like a Facebook post card on Home Screen
              if (isHomeScreen(currentScreenState) && showNativeAdCard) {
                FacebookNativeAdCard(
                  nativeAd = nativeAdState,
                  onClose = { showNativeAdCard = false },
                  modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                )
              }
            }

            // Banner Ad: ALWAYS at the bottom of the screen
            BannerAdView(
              adUnitId = ADMOB_BANNER_ID,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admob_banner_container")
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    nativeAdState?.destroy()
    super.onDestroy()
  }

  private fun isHomeScreen(screenId: String): Boolean {
    return screenId.isEmpty() ||
        screenId == "screen-home" ||
        screenId == "screen-welcome" ||
        screenId == "home"
  }

  /**
   * Load Interstitial Ad with automatic reloading on dismiss
   */
  fun loadInterstitialAd() {
    if (isInterstitialLoading || interstitialAd != null) return
    isInterstitialLoading = true

    val adRequest = AdRequest.Builder().build()
    InterstitialAd.load(
      this,
      ADMOB_INTERSTITIAL_ID,
      adRequest,
      object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
          Log.d(TAG, "Interstitial Ad loaded successfully")
          interstitialAd = ad
          isInterstitialLoading = false

          ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
              Log.d(TAG, "Interstitial Ad dismissed")
              interstitialAd = null
              loadInterstitialAd() // Pre-load next
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
              Log.w(TAG, "Interstitial failed to show: ${adError.message}")
              interstitialAd = null
              loadInterstitialAd()
            }
          }
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
          Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
          interstitialAd = null
          isInterstitialLoading = false
        }
      }
    )
  }

  /**
   * Show Interstitial Ad (called after game over)
   */
  fun showInterstitialAd() {
    Log.d(TAG, "Attempting to show Interstitial Ad...")
    if (interstitialAd != null) {
      interstitialAd?.show(this)
    } else {
      Log.d(TAG, "Interstitial Ad not ready yet, queuing load")
      loadInterstitialAd()
    }
  }

  /**
   * Load Native Ad (for Facebook post card presentation)
   */
  fun loadNativeAd() {
    val adLoader = AdLoader.Builder(this, ADMOB_NATIVE_ID)
      .forNativeAd { ad: NativeAd ->
        Log.d(TAG, "Native Ad loaded successfully: ${ad.headline}")
        nativeAdState?.destroy()
        nativeAdState = ad
      }
      .withAdListener(object : AdListener() {
        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
          Log.w(TAG, "Native Ad failed to load: ${loadAdError.message}")
        }
      })
      .build()

    adLoader.loadAd(AdRequest.Builder().build())
  }

  private fun cleanupCorruptedWebViewVariationsSeed() {
    try {
      val dataDir = applicationContext.dataDir
      val webviewDir = File(dataDir, "app_webview")
      if (webviewDir.exists() && webviewDir.isDirectory) {
        val seedFiles = listOf("variations_seed", "variations_seed_new", "variations_stamp")
        for (name in seedFiles) {
          val f = File(webviewDir, name)
          if (f.exists() && f.length() < 32L) {
            f.delete()
          }
        }
      }
    } catch (_: Throwable) {
      // Non-fatal safety check
    }
  }
}

/**
 * JavaScript interface to communicate from web game to native Android
 */
class WebAppInterface(
  private val onGameOver: () -> Unit,
  private val onScreenChanged: (String) -> Unit
) {
  @JavascriptInterface
  fun onGameOver() {
    onGameOver()
  }

  @JavascriptInterface
  fun onScreenChanged(screenId: String) {
    onScreenChanged(screenId)
  }
}

/**
 * Banner Ad Composable - Fixed at bottom of screen
 */
@Composable
fun BannerAdView(
  adUnitId: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(androidx.compose.ui.graphics.Color(0xFF070A13))
      .padding(vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      modifier = Modifier.fillMaxWidth(),
      factory = { context ->
        AdView(context).apply {
          setAdSize(AdSize.BANNER)
          this.adUnitId = adUnitId
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
          )
          loadAd(AdRequest.Builder().build())
        }
      }
    )
  }
}

/**
 * Native Ad styled like a Facebook post card on the Home Screen
 */
@Composable
fun FacebookNativeAdCard(
  nativeAd: NativeAd?,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = androidx.compose.ui.graphics.Color(0xFF141933)
    ),
    border = BorderStroke(1.5.dp, androidx.compose.ui.graphics.Color(0xFF2D3868)),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp)
      .testTag("facebook_native_ad_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp)
    ) {
      // Facebook Post Card Header with Close button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          // Facebook Logo badge / Avatar
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(androidx.compose.ui.graphics.Color(0xFF1877F2)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "f",
              color = androidx.compose.ui.graphics.Color.White,
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Column {
            Text(
              text = nativeAd?.headline ?: "Featured Sponsored Post",
              color = androidx.compose.ui.graphics.Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(androidx.compose.ui.graphics.Color(0xFF1E2950))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              ) {
                Text(
                  text = "Ad",
                  color = androidx.compose.ui.graphics.Color(0xFF00F0FF),
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Sponsored · 🌐 Public",
                color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                fontSize = 10.sp
              )
            }
          }
        }

        IconButton(
          onClick = onClose,
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Ad Card",
            tint = androidx.compose.ui.graphics.Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // If native ad is available from SDK, render full AdMob NativeAdView with binding
      if (nativeAd != null) {
        AndroidView(
          modifier = Modifier.fillMaxWidth(),
          factory = { context ->
            val inflater = LayoutInflater.from(context)
            val adView = inflater.inflate(R.layout.ad_native_facebook_card, null) as NativeAdView

            val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
            val bodyView = adView.findViewById<TextView>(R.id.ad_body)
            val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
            val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
            val mediaView = adView.findViewById<MediaView>(R.id.ad_media_view)
            val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)

            adView.headlineView = headlineView
            adView.bodyView = bodyView
            adView.callToActionView = ctaView
            adView.iconView = iconView
            adView.mediaView = mediaView
            adView.advertiserView = advertiserView

            headlineView?.text = nativeAd.headline
            bodyView?.text = nativeAd.body ?: ""
            ctaView?.text = nativeAd.callToAction ?: "INSTALL NOW"

            if (nativeAd.icon?.drawable != null) {
              iconView?.setImageDrawable(nativeAd.icon?.drawable)
              iconView?.visibility = View.VISIBLE
            } else {
              iconView?.visibility = View.GONE
            }

            if (nativeAd.mediaContent != null) {
              mediaView?.mediaContent = nativeAd.mediaContent
              mediaView?.visibility = View.VISIBLE
            } else {
              mediaView?.visibility = View.GONE
            }

            advertiserView?.text = nativeAd.advertiser ?: "Sponsored · 🌐"

            adView.setNativeAd(nativeAd)
            adView
          }
        )
      } else {
        // Facebook Card Layout Preview (while ad is loading or awaiting network fill)
        Text(
          text = "⚡ Challenge your friends in Tic-Tac-Toe Pro & claim exclusive rewards!",
          color = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
          fontSize = 12.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Facebook style reaction bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF0F1326))
            .padding(vertical = 6.dp, horizontal = 12.dp),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          Text(text = "👍 Like", color = androidx.compose.ui.graphics.Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Text(text = "💬 Comment", color = androidx.compose.ui.graphics.Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Text(text = "↗️ Share", color = androidx.compose.ui.graphics.Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TicTacToeGameScreen(
  onGameOver: () -> Unit,
  onScreenChanged: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }

  BackHandler {
    webViewInstance?.let { wv ->
      wv.evaluateJavascript(
        """
        (function() {
          var activeScreen = document.querySelector('.screen.active');
          if (activeScreen && activeScreen.id !== 'screen-home' && activeScreen.id !== 'screen-welcome') {
            var backBtn = activeScreen.querySelector('[data-nav-back]') || activeScreen.querySelector('[data-back]') || document.getElementById('btn-game-back');
            if (backBtn) { backBtn.click(); return true; }
          }
          return false;
        })();
        """.trimIndent()
      ) { result ->
        if (result != "true") {
          if (wv.canGoBack()) {
            wv.goBack()
          }
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.let { wv ->
        wv.stopLoading()
        wv.destroy()
      }
    }
  }

  AndroidView(
    factory = { context ->
      WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          mediaPlaybackRequiresUserGesture = false
          cacheMode = WebSettings.LOAD_DEFAULT
          useWideViewPort = true
          loadWithOverviewMode = true
        }

        // Bridge for native AdMob triggers (game over, screen tracking)
        addJavascriptInterface(
          WebAppInterface(
            onGameOver = onGameOver,
            onScreenChanged = onScreenChanged
          ),
          "AndroidBridge"
        )

        webChromeClient = object : WebChromeClient() {
          override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let {
              Log.d("TicTacToeWeb", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
            }
            return true
          }
        }

        webViewClient = object : WebViewClient() {
          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
          ) {
            super.onReceivedError(view, request, error)
            Log.w("TicTacToeWeb", "WebResourceError: ${error?.description}")
          }
        }

        loadUrl("file:///android_asset/index.html")
        webViewInstance = this
      }
    },
    update = { wv ->
      webViewInstance = wv
    },
    modifier = modifier
      .fillMaxSize()
      .testTag("tictactoe_webview")
  )
}
