package com.translate.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var etSourceLang: EditText
    private lateinit var etTargetLang: EditText
    private lateinit var btnSave: Button
    private lateinit var tvLangInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etSourceLang = findViewById(R.id.etSourceLang)
        etTargetLang = findViewById(R.id.etTargetLang)
        btnSave = findViewById(R.id.btnSave)
        tvLangInfo = findViewById(R.id.tvLangInfo)

        val prefs = getSharedPreferences("translate_prefs", MODE_PRIVATE)
        etSourceLang.setText(prefs.getString("source_lang", "auto"))
        etTargetLang.setText(prefs.getString("target_lang", "zh"))

        tvLangInfo.text = """
            常用语言代码：
            auto = 自动检测
            zh = 中文
            en = 英语
            ja = 日语
            ko = 韩语
            fr = 法语
            de = 德语
            es = 西班牙语
            ru = 俄语
            pt = 葡萄牙语
            it = 意大利语
            ar = 阿拉伯语
        """.trimIndent()

        btnSave.setOnClickListener {
            val source = etSourceLang.text.toString().trim()
            val target = etTargetLang.text.toString().trim()

            if (source.isEmpty() || target.isEmpty()) {
                Toast.makeText(this, "请输入语言代码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("source_lang", source)
                .putString("target_lang", target)
                .apply()

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
