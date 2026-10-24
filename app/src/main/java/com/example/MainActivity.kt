package com.example

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    cleanupCorruptedWebViewVariationsSeed()
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF070A13))
            .safeDrawingPadding()
            .testTag("game_root_container")
        ) {
          TicTacToeGameScreen()
        }
      }
    }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TicTacToeGameScreen(modifier: Modifier = Modifier) {
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
