package com.vietsnap

import android.graphics.Bitmap
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Offline OCR + vi→en translation. Cheap enough for a quick first answer before the cloud result. */
class OnDeviceTranslator {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.VIETNAMESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    @Volatile
    var modelReady = false
        private set

    /** Downloads the ~30MB vi→en model once; later launches are instant. */
    suspend fun prepare() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        modelReady = true
    }

    /** Returns null when no text is found or the translation model isn't downloaded yet. */
    suspend fun translate(bitmap: Bitmap): String? {
        if (!modelReady) return null
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = text.textBlocks.joinToString("\n") { it.text.replace('\n', ' ') }
        if (lines.isBlank()) return null
        return translator.translate(lines).await()
    }

    fun close() {
        recognizer.close()
        translator.close()
    }
}
