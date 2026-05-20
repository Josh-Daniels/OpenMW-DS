package org.openmw.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.openmw.ui.controls.UIStateManager.languageSet
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun TranslateText(
    context: Context,
    inputText: String,
    onTranslationResult: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val translatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(languageSet)
        .build()
    val translator = Translation.getClient(translatorOptions)

    LaunchedEffect(inputText) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Clean the input by removing "/storage/emulated/0/" and replacing slashes
                val cleanedInput = inputText
                    .replace("/storage/emulated/0/", "") // Remove the specified prefix
                    //.replace(Regex("[/\\\\]"), " ") // Replace slashes with spaces
                    .trim() // Remove leading/trailing whitespace
                //Log.d("Translation Debug", "Cleaned input: $cleanedInput")

                if (cleanedInput.isNotEmpty()) {

                    withContext(Dispatchers.Main) {
                        onTranslationResult("Translating...")
                        //Log.d("Translation Debug", "Translation started")
                    }

                    // Download model if needed and translate
                    val result = suspendCancellableCoroutine<String> { continuation ->
                        continuation.invokeOnCancellation {
                            //Log.d("Translation Debug", "Coroutine canceled")
                            translator.close() // Clean up translator resources
                            continuation.resumeWithException(CancellationException("Translation process was canceled"))
                        }

                        translator.downloadModelIfNeeded()
                            .addOnSuccessListener {
                                //Log.d("Translation Debug", "Model downloaded successfully")
                                translator.translate(cleanedInput)
                                    .addOnSuccessListener { translatedText ->
                                        //Log.d("Translation Debug", "Translated text: $translatedText")
                                        continuation.resume(translatedText)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("Translation Debug", "Translation failed: ${e.message}", e)
                                        continuation.resumeWithException(e)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("Translation Debug", "Model download failed: ${e.message}", e)
                                continuation.resumeWithException(e)
                            }
                    }

                    // Update translation state on the main thread
                    withContext(Dispatchers.Main) {
                        onTranslationResult(result)
                        //Log.d("Translation Debug", "Updated translation state: $result")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onTranslationResult("Invalid input path")
                        Log.d("Translation Debug", "Invalid input path")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMessage = "Translation failed: ${e.message ?: "Unknown error"}"
                    onTranslationResult(errorMessage)
                    Log.e("Translation Debug", "Translation error: ${e.message}", e)
                }
            }
        }
    }
}