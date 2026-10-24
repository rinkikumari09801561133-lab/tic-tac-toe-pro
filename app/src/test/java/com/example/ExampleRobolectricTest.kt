package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tic Tac Toe Pro", appName)
  }

  @Test
  fun `verify assets contain game index html and logo`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assetList = context.assets.list("")?.toList() ?: emptyList()
    assertTrue(assetList.contains("index.html"))
    assertTrue(assetList.contains("logo.png"))
  }
}
