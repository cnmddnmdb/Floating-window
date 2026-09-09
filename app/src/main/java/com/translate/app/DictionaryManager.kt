package com.translate.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class WordEntry(
    val word: String,
    val phonetic: String,
    val pos: String,
    val meaning: String
)

object DictionaryManager {

    private var dictionary: Map<String, WordEntry> = emptyMap()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        try {
            val inputStream = context.assets.open("dictionary.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.readText()
            reader.close()

            val jsonObject = JSONObject(jsonStr)
            val map = mutableMapOf<String, WordEntry>()

            for (key in jsonObject.keys()) {
                val entry = jsonObject.getJSONObject(key)
                map[key.lowercase()] = WordEntry(
                    word = key,
                    phonetic = entry.optString("phonetic", ""),
                    pos = entry.optString("pos", ""),
                    meaning = entry.optString("meaning", "")
                )
            }
            dictionary = map
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun lookup(word: String): WordEntry? {
        return dictionary[word.lowercase()]
    }

    fun lookupWithPhonetic(word: String): String {
        val entry = lookup(word)
        return if (entry != null) {
            "${entry.word} ${entry.phonetic} ${entry.pos} ${entry.meaning}"
        } else {
            word
        }
    }

    fun getDictionarySize(): Int = dictionary.size
}
