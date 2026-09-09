package com.translate.app

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etApiKey: EditText
    private lateinit var btnStart: Button
    private lateinit var btnSettings: Button
    private lateinit var tvStatus: TextView
    private lateinit var colorContainer: LinearLayout
    private lateinit var dotPreview: View
    private lateinit var seekR: SeekBar
    private lateinit var seekG: SeekBar
    private lateinit var seekB: SeekBar
    private lateinit var seekAlpha: SeekBar
    private lateinit var tvR: TextView
    private lateinit var tvG: TextView
    private lateinit var tvB: TextView
    private lateinit var tvAlpha: TextView
    private lateinit var spinnerWordBook: Spinner
    private var selectedColorIndex = -1
    private val colorViews = mutableListOf<View>()

    private val presetColors = listOf(
        "#FF7C4DFF",
        "#FF536DFE",
        "#FF2196F3",
        "#FF00BCD4",
        "#FF26A69A",
        "#FF4CAF50",
        "#FF8BC34A",
        "#FFFFEB3B",
        "#FFFFC107",
        "#FFFF9800",
        "#FFFF5722",
        "#FFF44336",
        "#FFE91E63",
        "#FF9C27B0",
        "#FF673AB7",
        "#FF3F51B5",
        "#FF795548",
        "#FF607D8B",
        "#FF9E9E9E",
        "#FF000000"
    )

    private val wordBookKeys = arrayOf("cet4", "cet6", "kaoyan", "gaokao", "zhongkao")
    private val wordBookNames = arrayOf("四级词汇", "六级词汇", "考研词汇", "高考词汇", "中考词汇")

    private var currentR = 124
    private var currentG = 77
    private var currentB = 255
    private var currentAlpha = 153

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化内置词典
        DictionaryManager.init(this)

        etApiKey = findViewById(R.id.etApiKey)
        btnStart = findViewById(R.id.btnStart)
        btnSettings = findViewById(R.id.btnSettings)
        tvStatus = findViewById(R.id.tvStatus)
        colorContainer = findViewById(R.id.colorContainer)
        dotPreview = findViewById(R.id.dotPreview)
        seekR = findViewById(R.id.seekR)
        seekG = findViewById(R.id.seekG)
        seekB = findViewById(R.id.seekB)
        seekAlpha = findViewById(R.id.seekAlpha)
        tvR = findViewById(R.id.tvR)
        tvG = findViewById(R.id.tvG)
        tvB = findViewById(R.id.tvB)
        tvAlpha = findViewById(R.id.tvAlpha)
        spinnerWordBook = findViewById(R.id.spinnerWordBook)

        val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("api_key", "")
        if (!savedKey.isNullOrEmpty()) {
            etApiKey.setText(savedKey)
        }

        val savedColor = prefs.getString("dot_color", "#FF7C4DFF") ?: "#FF7C4DFF"
        parseColor(savedColor)
        setupColorPicker()
        setupRGBSliders()
        updatePreview()

        val savedWordBook = prefs.getString("word_book", "cet4") ?: "cet4"
        val wordBookAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, wordBookNames)
        wordBookAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerWordBook.adapter = wordBookAdapter
        val savedIndex = wordBookKeys.indexOf(savedWordBook)
        if (savedIndex >= 0) spinnerWordBook.setSelection(savedIndex)
        spinnerWordBook.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("word_book", wordBookKeys[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnStart.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            prefs.edit().putString("api_key", apiKey).apply()

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1001)
            } else {
                startFloatingService()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val btnSquareFloat = findViewById<Button>(R.id.btnSquareFloat)
        btnSquareFloat.setOnClickListener {
            requestStoragePermission()
        }

        findViewById<Button>(R.id.btnSaveColor).setOnClickListener {
            val color = String.format("#%02X%02X%02X%02X", currentAlpha, currentR, currentG, currentB)
            prefs.edit().putString("dot_color", color).apply()
            Toast.makeText(this, "颜色已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseColor(color: String) {
        try {
            val c = android.graphics.Color.parseColor(color)
            currentAlpha = android.graphics.Color.alpha(c)
            currentR = android.graphics.Color.red(c)
            currentG = android.graphics.Color.green(c)
            currentB = android.graphics.Color.blue(c)
        } catch (e: Exception) {
            currentAlpha = 153
            currentR = 124
            currentG = 77
            currentB = 255
        }
        seekR.progress = currentR
        seekG.progress = currentG
        seekB.progress = currentB
        seekAlpha.progress = currentAlpha
    }

    private fun setupColorPicker() {
        colorViews.clear()
        for ((index, color) in presetColors.withIndex()) {
            val colorView = View(this)
            val size = resources.getDimensionPixelSize(R.dimen.color_size)
            val margin = resources.getDimensionPixelSize(R.dimen.color_margin)

            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(margin, margin, margin, margin)
            colorView.layoutParams = params

            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(android.graphics.Color.parseColor(color))
            bg.setStroke(3, android.graphics.Color.TRANSPARENT)
            colorView.background = bg

            colorView.setOnClickListener {
                selectedColorIndex = index
                parseColor(color)
                updateRGBSliders()
                updatePreview()
                updateColorSelection()
            }

            colorViews.add(colorView)
            colorContainer.addView(colorView)
        }
    }

    private fun updateColorSelection() {
        for ((index, view) in colorViews.withIndex()) {
            val bg = view.background as? GradientDrawable ?: continue
            if (index == selectedColorIndex) {
                bg.setStroke(4, android.graphics.Color.WHITE)
                view.alpha = 1f
            } else {
                bg.setStroke(3, android.graphics.Color.TRANSPARENT)
                view.alpha = 0.7f
            }
        }
    }

    private fun setupRGBSliders() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when (seekBar?.id) {
                    R.id.seekR -> { currentR = progress; tvR.text = "$progress" }
                    R.id.seekG -> { currentG = progress; tvG.text = "$progress" }
                    R.id.seekB -> { currentB = progress; tvB.text = "$progress" }
                    R.id.seekAlpha -> {
                        currentAlpha = progress
                        tvAlpha.text = "${(progress * 100 / 255)}%"
                    }
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekR.setOnSeekBarChangeListener(listener)
        seekG.setOnSeekBarChangeListener(listener)
        seekB.setOnSeekBarChangeListener(listener)
        seekAlpha.setOnSeekBarChangeListener(listener)
    }

    private fun updateRGBSliders() {
        tvR.text = "$currentR"
        tvG.text = "$currentG"
        tvB.text = "$currentB"
        tvAlpha.text = "${(currentAlpha * 100 / 255)}%"
    }

    private fun updatePreview() {
        val color = (currentAlpha shl 24) or (currentR shl 16) or (currentG shl 8) or currentB
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(color)
        dotPreview.background = bg
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        startService(intent)
        tvStatus.text = "悬浮窗已启动"
        Toast.makeText(this, "悬浮窗已启动，可按 Home 键返回桌面使用", Toast.LENGTH_LONG).show()
    }

    private fun startSquareFloatService() {
        Toast.makeText(this, "正在启动服务...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, SquareFloatService::class.java)
        startService(intent)
        tvStatus.text = "截屏翻译已启动"
        Toast.makeText(this, "方形悬浮窗已启动", Toast.LENGTH_LONG).show()
    }

    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startSquareFloatWithPermission()
        } else {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1003)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1003) {
            startSquareFloatWithPermission()
        }
    }

    private fun startSquareFloatWithPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1002)
        } else {
            startSquareFloatService()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 1002) {
            if (Settings.canDrawOverlays(this)) {
                startSquareFloatService()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
