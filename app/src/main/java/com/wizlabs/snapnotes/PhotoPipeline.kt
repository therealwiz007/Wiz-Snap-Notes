package com.wizlabs.snapnotes

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Capture → upright bitmap → stamp → private copy + thumbnail + phone gallery + database. */
object PhotoPipeline {
    private val executor = Executors.newSingleThreadExecutor()

    /** Longest side of the saved photo. Big enough to read a meter, small enough to mail fast. */
    private const val FULL_SIDE = 3264
    private const val THUMB_SIDE = 520
    const val PREVIEW_SIDE = 1280

    suspend fun capture(context: Context, ic: ImageCapture): File {
        val raw = File(context.cacheDir, "snap_raw_${System.nanoTime()}.jpg")
        return suspendCancellableCoroutine { cont ->
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(raw).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) = cont.resume(raw)
                    override fun onError(e: ImageCaptureException) {
                        raw.delete()
                        cont.resumeWithException(e)
                    }
                }
            )
        }
    }

    /** Decodes [file] no larger than [maxSide] and turns it upright (BitmapFactory ignores EXIF). */
    fun loadUpright(file: File, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        }
        var bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IllegalStateException("Could not read the photo")
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        val rotation = exif?.rotationDegrees ?: 0
        val flipped = exif?.isFlipped ?: false
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (rotation != 0 || flipped || scale < 1f) {
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rotation != 0) m.postRotate(rotation.toFloat())
            if (flipped) m.postScale(-1f, 1f)
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (t !== bmp) bmp.recycle()
            bmp = if (t.isMutable) t else t.copy(Bitmap.Config.ARGB_8888, true).also { t.recycle() }
        }
        return bmp
    }

    suspend fun save(
        context: Context,
        raw: File,
        header: String,
        body: String,
        emails: List<String>,
        timeMs: Long,
        toGallery: Boolean = true
    ): Note = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val store = NoteStore.get(app)
        val stampName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timeMs))
        val name = "SnapNote_${stampName}_${(System.nanoTime() % 10000).toString().padStart(4, '0')}.jpg"
        val full = File(store.notesDir, name)
        val thumb = File(store.thumbsDir, name)

        val bmp = loadUpright(raw, FULL_SIDE)
        try {
            Stamp.draw(Canvas(bmp), bmp.width, bmp.height, header, body, timeMs)
            FileOutputStream(full).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val s = THUMB_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            val small = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
            FileOutputStream(thumb).use { small.compress(Bitmap.CompressFormat.JPEG, 84, it) }
            small.recycle()
        } finally {
            bmp.recycle()
        }
        writeExif(full, header, body, timeMs)
        raw.delete()

        val gallery = if (toGallery) runCatching { copyToGallery(app, full, name, timeMs) }.getOrNull() else null
        val note = Note(0, timeMs, header.trim(), body.trim(), emails, full.absolutePath, thumb.absolutePath, gallery?.toString())
        val id = store.insert(note)
        note.copy(id = id)
    }

    private fun writeExif(file: File, header: String, body: String, timeMs: Long) = runCatching {
        val ex = ExifInterface(file.absolutePath)
        val stamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(timeMs))
        ex.setAttribute(ExifInterface.TAG_SOFTWARE, "Wiz Snap Notes")
        ex.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        ex.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        ex.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, listOf(header, body).filter { it.isNotBlank() }.joinToString(" | ").take(1800))
        ex.saveAttributes()
    }

    private fun copyToGallery(app: Context, file: File, name: String, timeMs: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wiz Snap Notes")
            put(MediaStore.Images.Media.DATE_TAKEN, timeMs)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Gallery unavailable")
        try {
            resolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { it.copyTo(out) } }
                ?: throw IllegalStateException("Gallery write failed")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Removes the note everywhere. The gallery copy goes too, when the app still owns it. */
    suspend fun delete(context: Context, note: Note) = withContext(Dispatchers.IO) {
        note.galleryUri?.let { u -> runCatching { context.contentResolver.delete(Uri.parse(u), null, null) } }
        NoteStore.get(context).delete(note)
    }
}
