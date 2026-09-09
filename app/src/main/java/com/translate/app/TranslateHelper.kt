package com.translate.app

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TranslateResult(
    val translation: String,
    val wordExplanations: List<Triple<String, String, Boolean>>
)

object TranslateHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        apiKey: String,
        wordBookKey: String = "cet4"
    ): TranslateResult {
        val actualSourceLang = if (sourceLang.equals("auto", ignoreCase = true)) "autodetect" else sourceLang
        val langPair = "$actualSourceLang|$targetLang"
        val url = "https://api.mymemory.translated.net/get?q=${java.net.URLEncoder.encode(text, "UTF-8")}&langpair=$langPair"

        val requestBuilder = Request.Builder().url(url)
        if (apiKey.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body?.string() ?: throw IOException("Empty response")
        val json = JSONObject(body)

        val translatedText = json
            .optJSONObject("responseData")
            ?.optString("translatedText", "翻译失败")
            ?: "翻译失败"

        val selectedWordBook = WordBooks.getWordBook(wordBookKey)

        val words = extractImportantWords(text)
        val explanations = mutableListOf<Triple<String, String, Boolean>>()

        for (word in words) {
            val lower = word.lowercase()
            val isBookWord = selectedWordBook.contains(lower)

            // 优先使用内置词典
            val dictEntry = DictionaryManager.lookup(lower)
            if (dictEntry != null) {
                explanations.add(Triple(word, "${dictEntry.phonetic} ${dictEntry.pos} ${dictEntry.meaning}", isBookWord))
            } else {
                // 内置词典没有，尝试在线翻译
                try {
                    val def = lookupWordOnline(lower)
                    if (def != null) {
                        explanations.add(Triple(word, def, isBookWord))
                    }
                } catch (_: Exception) {}
            }
        }

        return TranslateResult(translatedText, explanations)
    }

    private fun extractImportantWords(text: String): List<String> {
        val words = text.split(Regex("[^a-zA-Z']+"))
            .filter { it.length > 2 }
            .distinct()
        return words
    }

    private fun lookupWordOnline(word: String): String? {
        val candidates = mutableListOf(word)
        if (word.endsWith("s") && word.length > 4) candidates.add(word.dropLast(1))
        if (word.endsWith("es") && word.length > 5) candidates.add(word.dropLast(2))
        if (word.endsWith("ies") && word.length > 4) candidates.add(word.dropLast(3) + "y")
        if (word.endsWith("ed") && word.length > 4) candidates.add(word.dropLast(2))
        if (word.endsWith("ing") && word.length > 5) {
            candidates.add(word.dropLast(3))
            candidates.add(word.dropLast(3) + "e")
        }
        if (word.endsWith("er") && word.length > 4) candidates.add(word.dropLast(2))
        if (word.endsWith("est") && word.length > 5) candidates.add(word.dropLast(3))

        for (candidate in candidates) {
            val zh = translateWordToZh(candidate)
            if (zh.isNotEmpty()) return zh
        }
        return null
    }

    private fun translateWordToZh(word: String): String {
        val encoded = java.net.URLEncoder.encode(word, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|zh"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return ""
        val body = response.body?.string() ?: return ""
        val json = JSONObject(body)
        val result = json.optJSONObject("responseData")
            ?.optString("translatedText", "")
            ?: ""
        if (result.equals(word, ignoreCase = true)) return ""
        return result
    }

    private fun translatePos(pos: String): String {
        return when (pos) {
            "noun" -> "名词"
            "verb" -> "动词"
            "adjective" -> "形容词"
            "adverb" -> "副词"
            "pronoun" -> "代词"
            "preposition" -> "介词"
            "conjunction" -> "连词"
            "interjection" -> "感叹词"
            else -> pos
        }
    }

    private fun translateToZh(text: String): String {
        val encoded = java.net.URLEncoder.encode(text, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|zh"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return text
        val body = response.body?.string() ?: return text
        val json = JSONObject(body)
        return json.optJSONObject("responseData")
            ?.optString("translatedText", text)
            ?: text
    }
}
