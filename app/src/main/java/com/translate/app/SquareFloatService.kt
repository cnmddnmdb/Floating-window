package com.translate.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*

class SquareFloatService : Service() {

    private lateinit var windowManager: WindowManager
    private var squareView: View? = null
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var showingOverlay = false
    private var lastScreenshotId = 0L

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lastScreenshotId = getLatestScreenshotId()
        showSquareDot()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSquareDot() {
        val view = LayoutInflater.from(this).inflate(R.layout.float_square, null)
        squareView = view

        val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
        val color = Color.parseColor(prefs.getString("dot_color", "#FF7C4DFF") ?: "#FF7C4DFF")

        val dot = view.findViewById<View>(R.id.squareDot)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(color)
            setStroke(2, Color.argb(60, 255, 255, 255))
        }

        val dm = resources.displayMetrics
        params = WindowManager.LayoutParams(120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - 160
            y = 500
        }

        windowManager.addView(view, params)

        var tx = 0f; var ty = 0f; var px = 0; var py = 0; var drag = false

        dot.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    px = params.x; py = params.y
                    tx = e.rawX; ty = e.rawY
                    drag = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (!drag && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) drag = true
                    if (drag) {
                        params.x = px + dx.toInt()
                        params.y = py + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) openOverlay()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun openOverlay() {
        if (showingOverlay) return
        showingOverlay = true

        val view = LayoutInflater.from(this).inflate(R.layout.selection_overlay, null)
        overlayView = view

        val dm = resources.displayMetrics
        val sw = dm.widthPixels; val sh = dm.heightPixels

        windowManager.addView(view, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ))

        val frame = view.findViewById<View>(R.id.selectionFrame)
        val hint = view.findViewById<TextView>(R.id.tvHint)

        val fw = sw * 2 / 3
        val fh = fw * 3 / 5

        frame.post {
            (frame.layoutParams as FrameLayout.LayoutParams).apply {
                width = fw; height = fh
                leftMargin = (sw - fw) / 2
                topMargin = (sh - fh) / 2
                gravity = Gravity.TOP or Gravity.START
                frame.layoutParams = this
            }
        }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var ow = 0; var oh = 0; var mode = -1

        frame.setOnTouchListener { v, e ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY
                    ox = lp.leftMargin; oy = lp.topMargin
                    ow = lp.width; oh = lp.height

                    val tx = e.x.toInt(); val ty = e.y.toInt()
                    val b = 50
                    mode = when {
                        tx < b && ty < b -> 1
                        tx > ow - b && ty < b -> 2
                        tx < b && ty > oh - b -> 3
                        tx > ow - b && ty > oh - b -> 4
                        tx < b -> 5
                        tx > ow - b -> 6
                        ty < b -> 7
                        ty > oh - b -> 8
                        else -> 0
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt()
                    val dy = (e.rawY - sy).toInt()

                    when (mode) {
                        0 -> { lp.leftMargin = (ox + dx).coerceIn(0, sw - lp.width); lp.topMargin = (oy + dy).coerceIn(0, sh - lp.height) }
                        1 -> { lp.width = (ow - dx).coerceIn(200, sw); lp.height = (oh - dy).coerceIn(150, sh); lp.leftMargin = (ox + dx).coerceIn(0, sw - lp.width); lp.topMargin = (oy + dy).coerceIn(0, sh - lp.height) }
                        2 -> { lp.width = (ow + dx).coerceIn(200, sw); lp.height = (oh - dy).coerceIn(150, sh); lp.topMargin = (oy + dy).coerceIn(0, sh - lp.height) }
                        3 -> { lp.width = (ow - dx).coerceIn(200, sw); lp.height = (oh + dy).coerceIn(150, sh); lp.leftMargin = (ox + dx).coerceIn(0, sw - lp.width) }
                        4 -> { lp.width = (ow + dx).coerceIn(200, sw); lp.height = (oh + dy).coerceIn(150, sh) }
                        5 -> { lp.width = (ow - dx).coerceIn(200, sw); lp.leftMargin = (ox + dx).coerceIn(0, sw - lp.width) }
                        6 -> { lp.width = (ow + dx).coerceIn(200, sw) }
                        7 -> { lp.height = (oh - dy).coerceIn(150, sh); lp.topMargin = (oy + dy).coerceIn(0, sh - lp.height) }
                        8 -> { lp.height = (oh + dy).coerceIn(150, sh) }
                    }
                    v.layoutParams = lp
                    hint.text = "${lp.width} x ${lp.height}"
                    true
                }
                MotionEvent.ACTION_UP -> { hint.text = "拖动调整 | 点击识别截图"; true }
                else -> false
            }
        }

        view.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val fx = frame.left; val fy = frame.top
                val fw2 = frame.width; val fh2 = frame.height
                val tx = e.rawX.toInt(); val ty = e.rawY.toInt()
                if (tx < fx || tx > fx + fw2 || ty < fy || ty > fy + fh2) {
                    closeOverlay()
                }
            }
            false
        }

        view.findViewById<Button>(R.id.btnCloseOverlay).setOnClickListener { stopSelf() }

        view.findViewById<Button>(R.id.btnCapture).setOnClickListener {
            val lp = frame.layoutParams as FrameLayout.LayoutParams
            val rect = Rect(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin + lp.height)
            closeOverlay()
            waitForNewScreenshot(rect)
        }
    }

    private fun closeOverlay() {
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        showingOverlay = false
    }

    private fun getLatestScreenshotId(): Long {
        var id = 0L
        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%Screenshots%", "%截图%")
            contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use {
                if (it.moveToFirst()) id = it.getLong(0)
            }
        } catch (_: Exception) {}
        return id
    }

    private fun waitForNewScreenshot(rect: Rect) {
        Toast.makeText(this, "请按 电源键+音量下键 截图", Toast.LENGTH_LONG).show()
        lastScreenshotId = getLatestScreenshotId()

        scope.launch {
            var attempts = 0
            while (attempts < 40) {
                delay(500)
                attempts++
                val newId = getLatestScreenshotId()
                if (newId > lastScreenshotId) {
                    Toast.makeText(this@SquareFloatService, "检测到截图，识别中...", Toast.LENGTH_SHORT).show()
                    loadScreenshot(newId, rect)
                    return@launch
                }
            }
            Toast.makeText(this@SquareFloatService, "未检测到截图，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadScreenshot(id: Long, rect: Rect) {
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }

                if (bitmap == null) {
                    Toast.makeText(this@SquareFloatService, "读取截图失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val dm = resources.displayMetrics
                val screenW = dm.widthPixels
                val screenH = dm.heightPixels
                val scaleX = bitmap.width.toFloat() / screenW
                val scaleY = bitmap.height.toFloat() / screenH

                val x = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
                val y = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
                val w = (rect.width() * scaleX).toInt().coerceIn(1, bitmap.width - x)
                val h = (rect.height() * scaleY).toInt().coerceIn(1, bitmap.height - y)

                val region = Bitmap.createBitmap(bitmap, x, y, w, h)
                bitmap.recycle()

                recognizeText(region)
            } catch (e: Exception) {
                Toast.makeText(this@SquareFloatService, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun recognizeText(bmp: Bitmap) {
        Toast.makeText(this, "识别中...", Toast.LENGTH_SHORT).show()
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { r ->
                bmp.recycle()
                if (r.text.isNotBlank()) showResult(r.text)
                else Toast.makeText(this, "未识别到文字", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                bmp.recycle()
                Toast.makeText(this, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showResult(text: String) {
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_result, null)
        v.findViewById<TextView>(R.id.tvRecognized).text = text
        val tvTrans = v.findViewById<TextView>(R.id.tvTranslated)

        windowManager.addView(v, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ))

        v.findViewById<Button>(R.id.btnTranslateResult)?.apply {
            setOnClickListener { btn ->
                btn.isEnabled = false
                (btn as Button).text = "翻译中..."
                scope.launch {
                    try {
                        val key = getSharedPreferences("translate_prefs", MODE_PRIVATE).getString("api_key", "") ?: ""
                        val res = withContext(Dispatchers.IO) { TranslateHelper.translate(text, "autodetect", "zh", key, "cet4") }
                        tvTrans.text = res.translation
                    } catch (e: Exception) { tvTrans.text = "翻译失败: ${e.message}" }
                    btn.isEnabled = true
                    (btn as Button).text = "翻译"
                }
            }
        }

        v.findViewById<Button>(R.id.btnCloseResult)?.setOnClickListener {
            try { windowManager.removeView(v) } catch (_: Exception) {} }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        squareView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }
}
