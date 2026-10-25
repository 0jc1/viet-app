package com.vietsnap

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceTranslatorTest {

    @Test
    fun translatesVietnameseSign() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bmp = ctx.assets.open("sign.jpg").use { BitmapFactory.decodeStream(it) }
        val t = OnDeviceTranslator()
        t.prepare()
        t.translate(bmp) // warm-up run loads the native models
        val t0 = SystemClock.elapsedRealtime()
        val out = t.translate(bmp)
        val ms = SystemClock.elapsedRealtime() - t0
        Log.i("VietSnapTest", "on-device ${ms}ms: $out")
        assertTrue("got: $out", out != null && out.contains("smok", ignoreCase = true))
    }
}
