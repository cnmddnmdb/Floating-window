package com.translate.app

import android.app.Application
import android.content.Context
import java.lang.Thread

class TranslateApp : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
