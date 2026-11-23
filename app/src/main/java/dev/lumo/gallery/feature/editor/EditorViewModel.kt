package dev.lumo.gallery.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lumo.gallery.core.data.repo.LibraryRepository
import dev.lumo.gallery.core.data.repo.MediaRepository
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.image.Thumbnailer
import dev.lumo.gallery.core.model.MediaItem
import dev.lumo.gallery.core.ui.viewModelFactoryOf
import dev.lumo.gallery.core.util.MediaExporter
import dev.lumo.gallery.di.AppContainer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CropRatio(val label: String, val aspect: Float?) {
    FULL("原图", null),
    SQUARE("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f)
}

data class EditorState(
    val loading: Boolean = true,
    val preview: File? = null,
    val item: MediaItem? = null,
    val rotationQuarter: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRatio: CropRatio = CropRatio.FULL,
    val watermark: String = "",
    val watermarkPosition: Int = 8,
    val watermarkOpacity: Float = 0.75f,
    val note: String = "",
    val saving: Boolean = false
)

/**
 * 轻量编辑：旋转、翻转、比例裁剪、文字水印、备注。
 * 只另存副本，不修改原图；保存后写入系统相册 Pictures/拾光。
 */
class EditorViewModel(
    private val libraryId: String,
    private val itemId: String,
    private val context: Context,
    private val libraries: LibraryRepository,
    private val media: MediaRepository,
    private val thumbnailer: Thumbnailer,
    private val provider: MediaStreamProvider
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) { media.get(itemId) }
            if (item == null) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            val library = libraries.get(item.libraryId)
            val preview = withContext(Dispatchers.IO) {
                if (library == null) null
                else thumbnailer.generate(
                    library = library,
                    item = item,
                    provider = provider,
                    maxSize = 1600,
                    kind = "editor"
                )?.file
            }
            _state.value = _state.value.copy(
                loading = false,
                item = item,
                preview = preview,
                note = item.note.orEmpty(),
                watermarkPosition = 8
            )
        }
    }

    fun rotate() {
        _state.value = _state.value.copy(
            rotationQuarter = (_state.value.rotationQuarter + 1) % 4
        )
    }

    fun flipHorizontal() {
        _state.value = _state.value.copy(flipHorizontal = !_state.value.flipHorizontal)
    }

    fun flipVertical() {
        _state.value = _state.value.copy(flipVertical = !_state.value.flipVertical)
    }

    fun setCropRatio(ratio: CropRatio) {
        _state.value = _state.value.copy(cropRatio = ratio)
    }

    fun setWatermark(text: String) {
        _state.value = _state.value.copy(watermark = text)
    }

    fun setWatermarkPosition(position: Int) {
        _state.value = _state.value.copy(watermarkPosition = position)
    }

    fun setWatermarkOpacity(opacity: Float) {
        _state.value = _state.value.copy(watermarkOpacity = opacity.coerceIn(0.1f, 1f))
    }

    fun setNote(note: String) {
        _state.value = _state.value.copy(note = note)
    }

    fun save() {
        val item = _state.value.item ?: return
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val library = libraries.get(item.libraryId)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val original = provider.open(library ?: return@runCatching false, item)?.use { stream ->
                        decodeSampled(stream, maxDimension = 3200)
                    } ?: return@runCatching false
                    val edited = render(original)
                    original.recycle()
                    val name = buildString {
                        append(item.displayName.substringBeforeLast('.', item.displayName))
                        append("_edited_")
                        append(System.currentTimeMillis() / 1000)
                        append(".jpg")
                    }
                    val uri = MediaExporter.saveBitmap(context, edited, name)
                    edited.recycle()
                    uri != null
                }.getOrDefault(false)
            }
            if (ok) {
                val note = _state.value.note.trim()
                media.setNote(item.id, note.ifEmpty { null })
                _messages.emit("已保存到系统相册 Pictures/拾光")
            } else {
                _messages.emit("保存失败")
            }
            _state.value = _state.value.copy(saving = false)
        }
    }

    private fun decodeSampled(stream: java.io.InputStream, maxDimension: Int): Bitmap? {
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun render(source: Bitmap): Bitmap {
        val state = _state.value
        val matrix = Matrix().apply {
            postRotate(state.rotationQuarter * 90f)
            if (state.flipHorizontal) postScale(-1f, 1f)
            if (state.flipVertical) postScale(1f, -1f)
        }
        var bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        val ratio = state.cropRatio.aspect
        if (ratio != null) {
            val current = bitmap.width.toFloat() / bitmap.height
            val (w, h) = if (current > ratio) {
                (bitmap.height * ratio).toInt() to bitmap.height
            } else {
                bitmap.width to (bitmap.width / ratio).toInt()
            }
            val x = (bitmap.width - w) / 2
            val y = (bitmap.height - h) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
            if (cropped !== bitmap) bitmap.recycle()
            bitmap = cropped
        }

        if (state.watermark.isNotBlank()) {
            val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            if (mutable !== bitmap) bitmap.recycle()
            bitmap = mutable
            val canvas = Canvas(mutable)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = (state.watermarkOpacity * 255).toInt().coerceIn(20, 255)
                textSize = (minOf(mutable.width, mutable.height) * 0.045f).coerceAtLeast(12f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
            }
            val textWidth = paint.measureText(state.watermark)
            val margin = minOf(mutable.width, mutable.height) * 0.04f
            val position = state.watermarkPosition
            val column = position % 3
            val row = position / 3
            val x = when (column) {
                0 -> margin
                1 -> (mutable.width - textWidth) / 2f
                else -> mutable.width - textWidth - margin
            }
            val y = when (row) {
                0 -> margin + paint.textSize
                1 -> mutable.height / 2f
                else -> mutable.height - margin
            }
            canvas.drawText(state.watermark, x.coerceAtLeast(0f), y.coerceIn(paint.textSize, mutable.height.toFloat()), paint)
        }
        return bitmap
    }

    companion object {
        fun factory(
            container: AppContainer,
            libraryId: String,
            itemId: String
        ): ViewModelProvider.Factory = viewModelFactoryOf {
            EditorViewModel(
                libraryId = libraryId,
                itemId = itemId,
                context = container.appContext,
                libraries = container.libraryRepository,
                media = container.mediaRepository,
                thumbnailer = container.thumbnailer,
                provider = container.streamProvider
            )
        }
    }
}
