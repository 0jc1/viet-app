package com.vietsnap

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.vietsnap.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val cloud = OpenRouterClient(BuildConfig.OPENROUTER_API_KEY)
    private val onDevice = OnDeviceTranslator()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var shotJob: Job? = null

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else b.status.text = "Camera permission is required."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // Android 15 draws edge-to-edge; keep the controls above the navigation bar.
        val sheetPad = b.sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.sheet) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sheetPad + bars.bottom)
            insets
        }

        b.quickSwitch.isChecked = prefs.getBoolean("quick", true)
        b.quickSwitch.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("quick", on).apply()
            if (on) prepareOnDevice()
        }
        b.shutter.setOnClickListener { if (b.frozen.visibility == View.VISIBLE) retake() else snap() }
        setUpTapToFocus()

        if (BuildConfig.OPENROUTER_API_KEY.isEmpty()) b.status.text = "No API key: add OPENROUTER_API_KEY to .env and rebuild."
        else b.status.text = "Point at Vietnamese text and tap the shutter."

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
        if (b.quickSwitch.isChecked) prepareOnDevice()
    }

    override fun onResume() {
        super.onResume()
        cloud.warmUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDevice.close()
    }

    private fun prepareOnDevice() {
        if (onDevice.modelReady) return
        lifecycleScope.launch {
            runCatching { onDevice.prepare() }
                .onFailure { b.quickSwitch.text = "On-device quick pass (model download failed)" }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindPreview()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview() {
        val provider = cameraProvider ?: return
        // 720p keeps the preview pipeline cheap on a weak CPU while staying sharp enough for text.
        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                ).build()
            )
            .build()
            .also { it.setSurfaceProvider(b.preview.surfaceProvider) }
        provider.unbindAll()
        camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpTapToFocus() {
        b.preview.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val point = b.preview.meteringPointFactory.createPoint(e.x, e.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                v.performClick()
            }
            true
        }
    }

    private fun snap() {
        // Grabbing the preview frame is near-instant, unlike a full still capture on slow hardware.
        val frame = b.preview.bitmap ?: return
        b.frozen.setImageBitmap(frame)
        b.frozen.visibility = View.VISIBLE
        cameraProvider?.unbindAll() // free the CPU for OCR/encoding while we translate
        b.result.text = ""
        b.status.text = "Translating…"

        val t0 = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - t0
        var refined = false

        shotJob?.cancel()
        shotJob = lifecycleScope.launch {
            val scaled = withContext(Dispatchers.Default) { downscale(frame, 1024) }
            val jpeg = withContext(Dispatchers.Default) { encodeJpeg(scaled, 70) }

            // Cloud first so the upload starts before the on-device work competes for the CPU.
            val cloudJob = launch {
                runCatching {
                    cloud.translateImage(jpeg) { _, text ->
                        runOnUiThread {
                            refined = true
                            b.result.text = text
                        }
                    }
                }.onSuccess { text ->
                    refined = true
                    b.result.text = text
                    b.status.text = "Refined by image model in ${ms()} ms"
                }.onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    b.status.text = if (b.result.text.isNotEmpty()) {
                        "Quick result only (cloud failed: ${e.message?.take(80)})"
                    } else {
                        "Cloud failed: ${e.message?.take(120)}"
                    }
                }
            }

            if (b.quickSwitch.isChecked) {
                val quick = runCatching { onDevice.translate(scaled) }.getOrNull()
                if (!refined && quick != null) {
                    b.result.text = quick
                    b.status.text = "Quick (on-device) in ${ms()} ms · refining…"
                }
            }
            cloudJob.join()
        }
    }

    private fun retake() {
        shotJob?.cancel()
        b.frozen.visibility = View.GONE
        b.frozen.setImageDrawable(null)
        b.status.text = "Point at Vietnamese text and tap the shutter."
        bindPreview()
    }

    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val scale = maxSide.toFloat() / maxOf(src.width, src.height)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }

    private fun encodeJpeg(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
