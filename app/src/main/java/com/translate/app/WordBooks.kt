package com.translate.app

object WordBooks {

    val bookNames = mapOf(
        "cet4" to "四级词汇",
        "cet6" to "六级词汇",
        "kaoyan" to "考研词汇",
        "gaokao" to "高考词汇",
        "zhongkao" to "中考词汇"
    )

    fun getWordBook(key: String): Set<String> {
        return when (key) {
            "cet4" -> Cet4Words.set
            "cet6" -> Cet6Words.set
            "kaoyan" -> KaoyanWords.set
            "gaokao" -> GaokaoWords.set
            "zhongkao" -> ZhongkaoWords.set
            else -> Cet4Words.set
        }
    }
}
