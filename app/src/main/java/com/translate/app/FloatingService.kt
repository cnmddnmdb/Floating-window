package com.translate.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlinx.coroutines.*

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isExpanded = false
    private var isResultCollapsed = false

    private var dotX = 0
    private var dotY = 0
    private var windowX = 0
    private var windowY = 0
    private var hasWindowPosition = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createFloatingView()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_window, null)

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // 初始位置在屏幕右上方，避开边缘
        dotX = screenWidth - 150
        dotY = 200

        val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
        val dotColor = prefs.getString("dot_color", "#FF7C4DFF") ?: "#FF7C4DFF"

        val dotView = floatingView?.findViewById<View>(R.id.dotView)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.OVAL

        val color = android.graphics.Color.parseColor(dotColor)
        val alpha = android.graphics.Color.alpha(color)
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)

        val startColor = android.graphics.Color.argb(alpha, r, g, b)
        val centerColor = android.graphics.Color.argb(alpha / 2, r, g, b)
        val endColor = android.graphics.Color.argb(alpha / 4, r, g, b)

        val gradient = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(startColor, centerColor, endColor)
        )
        gradient.shape = android.graphics.drawable.GradientDrawable.OVAL
        dotView?.background = gradient

        val dragBarGradient = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                android.graphics.Color.argb(200, r, g, b),
                android.graphics.Color.argb(150, r, g, b),
                android.graphics.Color.argb(200, r, g, b)
            )
        )
        dragBarGradient.cornerRadius = 20f

        val bottomBarBg = android.graphics.drawable.GradientDrawable()
        bottomBarBg.setColor(android.graphics.Color.argb(180, r, g, b))
        bottomBarBg.cornerRadius = 4f

        val resultBg = android.graphics.drawable.GradientDrawable()
        resultBg.setColor(android.graphics.Color.argb(40, r, g, b))
        resultBg.cornerRadius = 12f

        val wordsBg = android.graphics.drawable.GradientDrawable()
        wordsBg.setColor(android.graphics.Color.argb(40, r, g, b))
        wordsBg.cornerRadius = 12f

        val editBg = android.graphics.drawable.GradientDrawable()
        editBg.setColor(android.graphics.Color.argb(30, r, g, b))
        editBg.setStroke(2, android.graphics.Color.argb(80, r, g, b))
        editBg.cornerRadius = 12f

        val translateBtnBg = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                android.graphics.Color.argb(220, r, g, b),
                android.graphics.Color.argb(180, r, g, b)
            )
        )
        translateBtnBg.cornerRadius = 12f

        val closeBtnBg = android.graphics.drawable.GradientDrawable()
        closeBtnBg.setColor(android.graphics.Color.argb(120, 255, 80, 80))
        closeBtnBg.cornerRadius = 10f

        val pasteBtnBg = android.graphics.drawable.GradientDrawable()
        pasteBtnBg.setColor(android.graphics.Color.argb(100, r, g, b))
        pasteBtnBg.cornerRadius = 10f

        val clearBtnBg = android.graphics.drawable.GradientDrawable()
        clearBtnBg.setColor(android.graphics.Color.argb(100, 255, 80, 80))
        clearBtnBg.cornerRadius = 10f

        val contentBg = android.graphics.drawable.GradientDrawable()
        contentBg.setColor(android.graphics.Color.argb(220, 20, 20, 30))
        contentBg.setCornerRadii(floatArrayOf(20f, 20f, 20f, 20f, 20f, 20f, 20f, 20f))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dotX
            y = dotY
        }

        windowManager.addView(floatingView, params)
        setupFloatingView()
    }

    private fun expandWithAnimation(dotView: View, contentLayout: LinearLayout, showContent: Boolean = false) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val windowWidth = 160 * displayMetrics.density

        if (hasWindowPosition) {
            params.x = windowX
            params.y = windowY
        }
        if (params.x + windowWidth > screenWidth - 20) {
            params.x = (screenWidth - windowWidth - 20).toInt()
        }
        if (params.x < 20) {
            params.x = 20
        }
        windowManager.updateViewLayout(floatingView, params)

        // 圆点快速缩走
        SpringAnimation(dotView, SpringAnimation.SCALE_X, 0f).apply {
            spring.stiffness = 3000f
            spring.dampingRatio = 1f
            start()
        }
        SpringAnimation(dotView, SpringAnimation.SCALE_Y, 0f).apply {
            spring.stiffness = 3000f
            spring.dampingRatio = 1f
            start()
        }
        SpringAnimation(dotView, SpringAnimation.ALPHA, 0f).apply {
            spring.stiffness = 3000f
            spring.dampingRatio = 1f
            start()
        }

        dotView.postDelayed({
            dotView.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
            contentLayout.alpha = 0f
            contentLayout.scaleX = 0.6f
            contentLayout.scaleY = 0.6f

            // 内容框Q弹放大
            SpringAnimation(contentLayout, SpringAnimation.SCALE_X, 1f).apply {
                spring.stiffness = 1800f
                spring.dampingRatio = 0.65f
                start()
            }
            SpringAnimation(contentLayout, SpringAnimation.SCALE_Y, 1f).apply {
                spring.stiffness = 1800f
                spring.dampingRatio = 0.65f
                start()
            }
            SpringAnimation(contentLayout, SpringAnimation.ALPHA, 1f).apply {
                spring.stiffness = 3000f
                spring.dampingRatio = 1f
                start()
            }
        }, 60)

        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(floatingView, params)
    }

    private fun collapseWithAnimation(dotView: View, contentLayout: LinearLayout) {
        windowX = params.x
        windowY = params.y
        hasWindowPosition = true

        // 内容框快速缩小
        SpringAnimation(contentLayout, SpringAnimation.SCALE_X, 0.3f).apply {
            spring.stiffness = 2500f
            spring.dampingRatio = 0.85f
            start()
        }
        SpringAnimation(contentLayout, SpringAnimation.SCALE_Y, 0.3f).apply {
            spring.stiffness = 2500f
            spring.dampingRatio = 0.85f
            start()
        }
        SpringAnimation(contentLayout, SpringAnimation.ALPHA, 0f).apply {
            spring.stiffness = 3000f
            spring.dampingRatio = 1f
            start()
        }

        contentLayout.postDelayed({
            contentLayout.visibility = View.GONE
            contentLayout.scaleX = 1f
            contentLayout.scaleY = 1f

            params.x = dotX
            params.y = dotY
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowManager.updateViewLayout(floatingView, params)

            // 圆点Q弹弹回
            dotView.alpha = 0f
            dotView.scaleX = 0f
            dotView.scaleY = 0f
            dotView.visibility = View.VISIBLE

            SpringAnimation(dotView, SpringAnimation.SCALE_X, 1f).apply {
                spring.stiffness = 1800f
                spring.dampingRatio = 0.6f
                start()
            }
            SpringAnimation(dotView, SpringAnimation.SCALE_Y, 1f).apply {
                spring.stiffness = 1800f
                spring.dampingRatio = 0.6f
                start()
            }
            SpringAnimation(dotView, SpringAnimation.ALPHA, 1f).apply {
                spring.stiffness = 3000f
                spring.dampingRatio = 1f
                start()
            }
        }, 80)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        val view = floatingView ?: return

        val dotView = view.findViewById<View>(R.id.dotView)
        val contentLayout = view.findViewById<LinearLayout>(R.id.contentLayout)
        val contentPanel = view.findViewById<LinearLayout>(R.id.contentPanel)
        val tvResult = view.findViewById<TextView>(R.id.tvResult)
        val tvWords = view.findViewById<TextView>(R.id.tvWords)
        val scrollWords = view.findViewById<ScrollView>(R.id.scrollWords)
        val etInput = view.findViewById<EditText>(R.id.etInput)
        val btnOneKey = view.findViewById<Button>(R.id.btnOneKey)
        val btnTranslate = view.findViewById<Button>(R.id.btnTranslate)
        val btnPaste = view.findViewById<Button>(R.id.btnPaste)
        val btnClear = view.findViewById<Button>(R.id.btnClear)
        val btnClose = view.findViewById<Button>(R.id.btnClose)
        val dragBar = view.findViewById<View>(R.id.dragBar)
        val bottomBar = view.findViewById<View>(R.id.bottomBar)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
        val dotColor = prefs.getString("dot_color", "#FF7C4DFF") ?: "#FF7C4DFF"
        val color = android.graphics.Color.parseColor(dotColor)
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)

        // === 第一行：青色拖动条 = 顶部圆角(topStart/topEnd=16dp)，底部直角 ===
        val barCornerPx = 16f * resources.displayMetrics.density
        dragBar.clipToOutline = true
        dragBar.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    outline.setPath(android.graphics.Path().apply {
                        addRoundRect(
                            android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                            floatArrayOf(
                                barCornerPx, barCornerPx,  // top-left: 圆角
                                barCornerPx, barCornerPx,  // top-right: 圆角
                                0f, 0f,                    // bottom-right: 直角
                                0f, 0f                     // bottom-left: 直角
                            ),
                            android.graphics.Path.Direction.CW
                        )
                    })
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, barCornerPx)
                }
            }
        }
        dragBar.setBackgroundColor(android.graphics.Color.argb(220, r, g, b))

        // === 第二行：黑色内容面板 = 顶部直角(紧贴拖动条底边)，底部圆角(16dp) ===
        val panelCornerPx = 16f * resources.displayMetrics.density
        contentPanel.clipToOutline = true
        contentPanel.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    outline.setPath(android.graphics.Path().apply {
                        addRoundRect(
                            android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                            floatArrayOf(
                                0f, 0f,                    // top-left: 直角（与拖动条底边拼接）
                                0f, 0f,                    // top-right: 直角（与拖动条底边拼接）
                                panelCornerPx, panelCornerPx, // bottom-right: 圆角
                                panelCornerPx, panelCornerPx  // bottom-left: 圆角
                            ),
                            android.graphics.Path.Direction.CW
                        )
                    })
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, panelCornerPx)
                }
            }
        }

        etInput.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(40, r, g, b))
            setStroke(2, android.graphics.Color.argb(100, r, g, b))
            cornerRadius = 12f
        }

        btnTranslate.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                android.graphics.Color.argb(230, r, g, b),
                android.graphics.Color.argb(200, r, g, b)
            )
        ).apply { cornerRadius = 12f }

        btnPaste.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(120, r, g, b))
            cornerRadius = 10f
        }

        btnClear.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(120, r, g, b))
            cornerRadius = 10f
        }

        btnClose.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(120, r, g, b))
            cornerRadius = 10f
        }

        tvResult.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(50, r, g, b))
            cornerRadius = 12f
        }

        tvWords.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(40, r, g, b))
            cornerRadius = 12f
        }

        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.argb(200, r, g, b)
        )

        val maxWordLines = 10

        fun adjustScrollHeight() {
            scrollWords.post {
                tvWords.post {
                    // 使用实际渲染行数判断，而非文本分割行数
                    val layout = tvWords.layout ?: return@post
                    val actualLineCount = layout.lineCount
                    val actualLineHeight = tvWords.lineHeight

                    if (actualLineCount > maxWordLines) {
                        val fixedHeight = maxWordLines * actualLineHeight + scrollWords.paddingTop + scrollWords.paddingBottom
                        scrollWords.layoutParams.height = fixedHeight
                    } else {
                        scrollWords.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    scrollWords.requestLayout()
                    scrollWords.postDelayed({
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        windowManager.updateViewLayout(floatingView, params)
                    }, 50)
                }
            }
        }

        var startTouchX = 0f
        var startTouchY = 0f
        var startPosX = 0
        var startPosY = 0
        var isDragging = false

        fun expandToFull() {
            expandWithAnimation(dotView, contentLayout)
            isExpanded = true
        }

        fun collapseToDot() {
            isExpanded = false
            // 确保收起时恢复 FLAG_NOT_FOCUSABLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(floatingView, params)
            collapseWithAnimation(dotView, contentLayout)
        }

        // 单击home键：收起/显示翻译结果和重点单词
        // SpringAnimation 物理弹簧动画，Q弹丝滑
        fun toggleResultCollapse() {
            if (tvResult.text.isEmpty() || tvResult.text == "翻译结果将显示在这里") {
                return
            }

            if (isResultCollapsed) {
                // 展开：从上方弹入 + 淡入
                tvResult.visibility = View.VISIBLE
                if (tvWords.text.isNotEmpty()) {
                    scrollWords.visibility = View.VISIBLE
                }

                tvResult.translationY = -60f
                tvResult.alpha = 0f
                val hasWords = tvWords.text.isNotEmpty()
                if (hasWords) {
                    scrollWords.translationY = -60f
                    scrollWords.alpha = 0f
                }

                // tvResult 弹入
                SpringAnimation(tvResult, SpringAnimation.TRANSLATION_Y, 0f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                    spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    start()
                }
                SpringAnimation(tvResult, SpringAnimation.ALPHA, 1f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_HIGH
                    spring.dampingRatio = 1f
                    start()
                }

                // scrollWords 延迟弹入（仅当有词汇时）
                if (hasWords) {
                    scrollWords.postDelayed({
                        SpringAnimation(scrollWords, SpringAnimation.TRANSLATION_Y, 0f).apply {
                            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                            start()
                        }
                        SpringAnimation(scrollWords, SpringAnimation.ALPHA, 1f).apply {
                            spring.stiffness = SpringForce.STIFFNESS_HIGH
                            spring.dampingRatio = 1f
                            start()
                        }
                    }, 40)
                    scrollWords.postDelayed({ adjustScrollHeight() }, 350)
                }
                isResultCollapsed = false
            } else {
                // 收起：向上弹出 + 淡出
                val hasWords = scrollWords.visibility == View.VISIBLE
                SpringAnimation(tvResult, SpringAnimation.TRANSLATION_Y, -60f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_HIGH
                    spring.dampingRatio = 0.8f
                    start()
                }
                SpringAnimation(tvResult, SpringAnimation.ALPHA, 0f).apply {
                    spring.stiffness = SpringForce.STIFFNESS_HIGH
                    spring.dampingRatio = 1f
                    start()
                }

                if (hasWords) {
                    SpringAnimation(scrollWords, SpringAnimation.TRANSLATION_Y, -60f).apply {
                        spring.stiffness = SpringForce.STIFFNESS_HIGH
                        spring.dampingRatio = 0.8f
                        start()
                    }
                    SpringAnimation(scrollWords, SpringAnimation.ALPHA, 0f).apply {
                        spring.stiffness = SpringForce.STIFFNESS_HIGH
                        spring.dampingRatio = 1f
                        start()
                    }
                }

                // 延迟隐藏，等动画完成
                tvResult.postDelayed({
                    tvResult.visibility = View.GONE
                    tvResult.translationY = 0f
                    if (hasWords) {
                        scrollWords.visibility = View.GONE
                        scrollWords.translationY = 0f
                    }
                }, 200)

                isResultCollapsed = true
            }
        }

        var longPressHandled = false
        val edgeMargin = 50 // 屏幕边缘不拦截的像素数（约15dp）

        dotView.setOnTouchListener { _, event ->
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            // 屏幕边缘放行系统手势
            if (x < edgeMargin || x > screenWidth - edgeMargin ||
                y < edgeMargin || y > screenHeight - edgeMargin) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    return@setOnTouchListener false
                }
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startPosX = params.x
                    startPosY = params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startTouchX
                    val dy = event.rawY - startTouchY
                    if (!isDragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startPosX + dx.toInt()
                        params.y = startPosY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        dotX = params.x
                        dotY = params.y
                    } else {
                        expandToFull()
                    }
                    true
                }
                else -> false
            }
        }

        dragBar.setOnTouchListener { _, event ->
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            // 屏幕边缘放行系统手势
            if (x < edgeMargin || x > screenWidth - edgeMargin ||
                y < edgeMargin || y > screenHeight - edgeMargin) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startPosX = params.x
                    startPosY = params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startTouchX
                    val dy = event.rawY - startTouchY
                    if (!isDragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startPosX + dx.toInt()
                        params.y = startPosY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        windowX = params.x
                        windowY = params.y
                        hasWindowPosition = true
                    }
                    true
                }
                else -> false
            }
        }

        var bottomStartY = 0f
        var isLongPressDragging = false
        val dismissThreshold = 200 // 向上拖动多少像素触发消失

        bottomBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bottomStartY = event.rawY
                    isLongPressDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = bottomStartY - event.rawY // 向上为正

                    // 向上或向下拖动超过 15px 触发长按拖动模式
                    if (Math.abs(dy) > 15) {
                        isLongPressDragging = true
                    }

                    if (isLongPressDragging) {
                        // 计算进度：向上为正(0~1)，向下为负(-1~0)
                        val rawProgress = dy / dismissThreshold
                        val progress = rawProgress.coerceIn(-1f, 1f)

                        // 统一计算 scale 和 alpha
                        // progress=1 时 scale=0.4, alpha=0.1
                        // progress=0 时 scale=1, alpha=1
                        // progress=-1 时 scale=1, alpha=1
                        val scale: Float
                        val alpha: Float

                        if (progress > 0) {
                            scale = 1f - progress * 0.6f
                            alpha = 1f - progress * 0.9f
                        } else {
                            scale = 1f
                            alpha = 1f
                        }

                        contentLayout.scaleX = scale.coerceAtLeast(0.4f)
                        contentLayout.scaleY = scale.coerceAtLeast(0.4f)
                        contentLayout.alpha = alpha.coerceAtLeast(0.1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isLongPressDragging) {
                        val dy = bottomStartY - event.rawY
                        val progress = (dy / dismissThreshold).coerceIn(-0.5f, 1f)

                        if (progress > 0.7f) {
                            // 拖动超过 70%，收起变回圆形
                            contentLayout.animate().cancel()

                            // 内容框快速缩小
                            SpringAnimation(contentLayout, SpringAnimation.SCALE_X, 0f).apply {
                                spring.stiffness = 2500f
                                spring.dampingRatio = 0.85f
                                start()
                            }
                            SpringAnimation(contentLayout, SpringAnimation.SCALE_Y, 0f).apply {
                                spring.stiffness = 2500f
                                spring.dampingRatio = 0.85f
                                start()
                            }
                            SpringAnimation(contentLayout, SpringAnimation.ALPHA, 0f).apply {
                                spring.stiffness = 3000f
                                spring.dampingRatio = 1f
                                start()
                            }

                            contentLayout.postDelayed({
                                contentLayout.visibility = View.GONE
                                contentLayout.scaleX = 1f
                                contentLayout.scaleY = 1f
                                contentLayout.alpha = 1f

                                params.x = dotX
                                params.y = dotY
                                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                                windowManager.updateViewLayout(floatingView, params)

                                // 圆点Q弹弹回
                                dotView.alpha = 0f
                                dotView.scaleX = 0f
                                dotView.scaleY = 0f
                                dotView.visibility = View.VISIBLE

                                SpringAnimation(dotView, SpringAnimation.SCALE_X, 1f).apply {
                                    spring.stiffness = 1800f
                                    spring.dampingRatio = 0.6f
                                    start()
                                }
                                SpringAnimation(dotView, SpringAnimation.SCALE_Y, 1f).apply {
                                    spring.stiffness = 1800f
                                    spring.dampingRatio = 0.6f
                                    start()
                                }
                                SpringAnimation(dotView, SpringAnimation.ALPHA, 1f).apply {
                                    spring.stiffness = 3000f
                                    spring.dampingRatio = 1f
                                    start()
                                }
                            }, 80)

                            isExpanded = false
                            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            windowManager.updateViewLayout(floatingView, params)
                        } else {
                            // 没超过，恢复原状
                            contentLayout.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                        }
                    } else {
                        // 没有拖动，单击即时响应
                        toggleResultCollapse()
                    }
                    true
                }
                else -> false
            }
        }

        btnPaste.setOnClickListener {
            // 先请求焦点，移除 FLAG_NOT_FOCUSABLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(floatingView, params)

            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                etInput.setText(text)
                etInput.setSelection(etInput.text.length)
            }
            // 粘贴后恢复 FLAG_NOT_FOCUSABLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(floatingView, params)
        }

        btnClear.setOnClickListener {
            etInput.text.clear()
        }

        btnOneKey.setOnClickListener {
            // 先请求焦点
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(floatingView, params)

            // 等待窗口更新后执行操作
            btnOneKey.post {
                // 如果结果区域被home键收起，自动展开
                if (isResultCollapsed) {
                    isResultCollapsed = false
                    tvResult.visibility = View.VISIBLE
                    scrollWords.visibility = View.VISIBLE
                    tvResult.translationY = 0f
                    tvResult.alpha = 1f
                    scrollWords.translationY = 0f
                    scrollWords.alpha = 1f
                }

                // 一键：清除 → 粘贴 → 翻译
                etInput.text.clear()

                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    etInput.setText(text)
                    etInput.setSelection(etInput.text.length)
                }

                // 立即恢复 FLAG_NOT_FOCUSABLE
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)

                // 有内容才触发翻译
                if (etInput.text.toString().trim().isNotEmpty()) {
                    btnTranslate.performClick()
                }
            }
        }

        btnTranslate.setOnClickListener {
            val inputText = etInput.text.toString().trim()
            if (inputText.isEmpty()) {
                Toast.makeText(this, "请输入要翻译的文本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 点击后立即恢复 FLAG_NOT_FOCUSABLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(floatingView, params)

            // 加载中只显示转圈，隐藏结果区域避免拉长悬浮窗
            progressBar.visibility = View.VISIBLE
            tvResult.visibility = View.GONE
            scrollWords.visibility = View.GONE
            tvResult.text = ""
            tvWords.text = ""

            val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "")
            val sourceLang = prefs.getString("source_lang", "auto") ?: "auto"
            val targetLang = prefs.getString("target_lang", "zh") ?: "zh"
            val wordBook = prefs.getString("word_book", "cet4") ?: "cet4"

            serviceScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        TranslateHelper.translate(inputText, sourceLang, targetLang, apiKey ?: "", wordBook)
                    }
                    progressBar.visibility = View.GONE
                    tvResult.visibility = View.VISIBLE
                    tvResult.text = result.translation
                    isResultCollapsed = false

                    if (result.wordExplanations.isNotEmpty()) {
                        val header = "--- 重点词汇解释 ---"
                        val fullText = StringBuilder(header)
                        val cet4Words = mutableListOf<Pair<String, Int>>()

                        for ((word, explanation, isCet4) in result.wordExplanations) {
                            fullText.appendLine()
                            val lineStart = fullText.length
                            fullText.append("· $word: $explanation")
                            if (isCet4) {
                                cet4Words.add("· $word" to lineStart)
                            }
                        }

                        val spannable = android.text.SpannableString(fullText.toString())
                        for ((cet4Line, start) in cet4Words) {
                            val end = start + cet4Line.length
                            if (end <= spannable.length) {
                                spannable.setSpan(
                                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FFD54F")),
                                    start, end,
                                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                spannable.setSpan(
                                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                                    start, end,
                                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                        tvWords.text = spannable
                        scrollWords.visibility = View.VISIBLE
                        adjustScrollHeight()
                    }
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    tvResult.visibility = View.VISIBLE
                    tvResult.text = "翻译失败: ${e.message}"
                }
                // 延迟恢复 FLAG_NOT_FOCUSABLE，确保 UI 更新完成
                btnTranslate.postDelayed({
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(floatingView, params)
                }, 100)
            }
        }

        // 输入框获取焦点时临时移除 FLAG_NOT_FOCUSABLE，失去焦点后立即恢复
        etInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(floatingView, params)
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)
            }
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        collapseToDot()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        floatingView?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}
