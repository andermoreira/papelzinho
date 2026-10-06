package com.andersonmoreira.papelzinho

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREVIEW_DEBOUNCE_MS = 180L

class NoteViewModel(
    application: Application,
) : AndroidViewModel(application) {
    var text by mutableStateOf("")
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var error by mutableStateOf<Int?>(null)
        private set
    var settings by mutableStateOf(NoteSettings())
        private set
    var preparing by mutableStateOf(false)
        private set
    private val renderer = TextImageRenderer()
    private val settingsStore = SettingsStore(application)
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect {
                settings = it
                updatePreview()
            }
        }
    }

    fun updateText(value: String) {
        text = value
        error = null
        updatePreview()
    }

    fun chooseSettings(value: NoteSettings) {
        settings = value
        updatePreview()
        viewModelScope.launch { settingsStore.save(value) }
    }

    private fun style() = RenderStyle(height = settings.height, palette = settings.palette)

    private fun updatePreview() {
        previewJob?.cancel()
        previewJob =
            viewModelScope.launch {
                delay(PREVIEW_DEBOUNCE_MS)
                val previous = preview
                val result =
                    if (text.isBlank()) {
                        null
                    } else {
                        renderer.renderPreview(
                            text,
                            style(),
                            NoteDimensions.PREVIEW_WIDTH,
                        )
                    }
                preview = result?.getOrNull()
                error =
                    when (result?.exceptionOrNull()) {
                        is TextTooLong -> R.string.text_too_long
                        null -> null
                        else -> R.string.render_error
                    }
                previous?.recycle()
            }
    }

    fun prepare(
        sample: Boolean,
        ready: (ByteArray) -> Unit,
    ) {
        if (preparing) return
        val content = if (sample) getApplication<Application>().getString(R.string.test_image_text) else text
        if (content.isBlank()) {
            error = R.string.empty_error
            return
        }
        preparing = true
        viewModelScope.launch {
            try {
                val result = renderer.render(content, style())
                val bytes = result.getOrNull()
                if (bytes !=
                    null
                ) {
                    ready(bytes)
                } else {
                    error =
                        if (result.exceptionOrNull() is TextTooLong) R.string.text_too_long else R.string.render_error
                }
            } finally {
                preparing = false
            }
        }
    }

    fun showError(resource: Int) {
        error = resource
    }

    override fun onCleared() {
        preview?.recycle()
        preview = null
        text = ""
        super.onCleared()
    }
}
