package dev.lumo.gallery.core.image

import androidx.exifinterface.media.ExifInterface
import dev.lumo.gallery.core.data.source.MediaStreamProvider
import dev.lumo.gallery.core.model.Library
import dev.lumo.gallery.core.model.MediaItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Locale

data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val focalLength: String? = null,
    val dateTimeOriginal: Long? = null,
    val whiteBalance: String? = null,
    val flash: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val software: String? = null,
    val artist: String? = null,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null
) {
    val hasCameraInfo: Boolean
        get() = listOf(make, model, lens, iso, aperture, shutter, focalLength).any { !it.isNullOrBlank() }

    val hasGps: Boolean get() = latitude != null && longitude != null
}

/** 读取完整 EXIF（相机、曝光、焦距、GPS）。 */
object ExifReader {

    suspend fun read(
        provider: MediaStreamProvider,
        library: Library,
        item: MediaItem,
        io: CoroutineDispatcher = Dispatchers.IO
    ): ExifInfo? = withContext(io) {
        provider.open(library, item)?.use { raw -> readFromStream(raw) }
    }

    fun readFromStream(raw: InputStream): ExifInfo? = runCatching {
        BufferedInputStream(raw).use { input ->
            val exif = ExifInterface(input)
            val latLong = runCatching { exif.latLong }.getOrNull()
            ExifInfo(
                make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifEmpty { null },
                model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifEmpty { null },
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null },
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
                aperture = formatAperture(exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)),
                shutter = formatShutter(exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)),
                focalLength = formatFocal(
                    exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                ),
                dateTimeOriginal = ExifDates.parse(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ),
                whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE),
                flash = exif.getAttribute(ExifInterface.TAG_FLASH),
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1),
                software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()?.ifEmpty { null },
                artist = exif.getAttribute(ExifInterface.TAG_ARTIST)?.trim()?.ifEmpty { null },
                pixelWidth = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
                    .takeIf { it > 0 },
                pixelHeight = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
                    .takeIf { it > 0 }
            )
        }
    }.getOrNull()

    private fun formatAperture(value: Double): String? =
        if (value > 0) String.format(Locale.US, "f/%.1f", value) else null

    private fun formatShutter(seconds: Double): String? = when {
        seconds <= 0 -> null
        seconds >= 1 -> String.format(Locale.US, "%.1fs", seconds)
        else -> "1/${(1.0 / seconds).toInt()}s"
    }

    private fun formatFocal(mm: Double): String? =
        if (mm > 0) String.format(Locale.US, "%.0fmm", mm) else null
}
