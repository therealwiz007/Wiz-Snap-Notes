package com.wizlabs.snapnotes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small in-memory cache of grid thumbnails (about 16 MB), so scrolling never re-decodes. */
object Thumbs {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(path: String): ImageBitmap? = cache.get(path)

    suspend fun load(path: String, maxSide: Int = 520): ImageBitmap? = cache.get(path) ?: withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.exists()) return@withContext null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // half the memory; photos don't need alpha
        }) ?: return@withContext null
        bmp.asImageBitmap().also { if (maxSide <= 520) cache.put(path, it) }
    }

    fun evict(path: String) { cache.remove(path) }
}

@Composable
fun rememberImage(path: String, maxSide: Int = 520): State<ImageBitmap?> =
    produceState(initialValue = Thumbs.cached(path), path, maxSide) {
        if (value == null) value = Thumbs.load(path, maxSide)
    }

/**
 * The home-screen hero art (two stacked photo notes), painted once into a bitmap. The screen
 * only moves that bitmap with graphicsLayer, so the float animation costs almost nothing.
 */
object HeroArt {
    private var art: ImageBitmap? = null

    fun get(px: Int): ImageBitmap {
        art?.takeIf { it.width == px }?.let { return it }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val u = px / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun card(rot: Float, dx: Float, dy: Float, face: Int, alpha: Int, lens: Boolean) {
            c.save()
            c.rotate(rot, px / 2f, px / 2f)
            c.translate(dx * u, dy * u)
            val r = RectF(24 * u, 14 * u, 76 * u, 86 * u)
            p.color = 0x55000000; p.alpha = (alpha * 0.4f).toInt()
            c.drawRoundRect(RectF(r.left + 1.5f * u, r.top + 3 * u, r.right + 1.5f * u, r.bottom + 3 * u), 6 * u, 6 * u, p)
            p.color = face; p.alpha = alpha
            c.drawRoundRect(r, 6 * u, 6 * u, p)
            // header band + accent
            p.color = 0xFF0B0D10.toInt(); p.alpha = alpha
            c.drawRoundRect(RectF(r.left + 4 * u, r.top + 4 * u, r.right - 4 * u, r.top + 14 * u), 2.5f * u, 2.5f * u, p)
            p.color = 0xFFFFC400.toInt(); p.alpha = alpha
            c.drawRoundRect(RectF(r.left + 6.5f * u, r.top + 6.5f * u, r.left + 8 * u, r.top + 11.5f * u), u, u, p)
            p.color = 0xFFF4F5F7.toInt(); p.alpha = alpha
            c.drawRoundRect(RectF(r.left + 10.5f * u, r.top + 7 * u, r.left + 32 * u, r.top + 9 * u), u, u, p)
            p.color = 0xFFFFC400.toInt(); p.alpha = alpha
            c.drawRoundRect(RectF(r.left + 10.5f * u, r.top + 10.5f * u, r.left + 22 * u, r.top + 12 * u), u, u, p)
            if (lens) {
                val cx = px / 2f; val cy = r.top + 38 * u
                p.color = 0xFF0B0D10.toInt(); c.drawCircle(cx, cy, 13 * u, p)
                p.color = 0xFFFFC400.toInt(); c.drawCircle(cx, cy, 8 * u, p)
                p.color = 0xFF0B0D10.toInt(); c.drawCircle(cx, cy, 4.2f * u, p)
                p.color = 0xFFFFE27A.toInt(); c.drawCircle(cx + 2.4f * u, cy - 2.4f * u, 1.4f * u, p)
            }
            // note lines
            p.color = 0xFF0B0D10.toInt(); p.alpha = (alpha * 0.85f).toInt()
            c.drawRoundRect(RectF(r.left + 5 * u, r.bottom - 15 * u, r.right - 5 * u, r.bottom - 12.5f * u), u, u, p)
            c.drawRoundRect(RectF(r.left + 5 * u, r.bottom - 9.5f * u, r.right - 18 * u, r.bottom - 7 * u), u, u, p)
            c.restore()
        }
        card(9f, 6f, 1f, 0xFF2A2F38.toInt(), 255, false)
        card(-7f, -3f, 0f, 0xFFFFC400.toInt(), 255, true)
        return bmp.asImageBitmap().also { art = it }
    }
}
