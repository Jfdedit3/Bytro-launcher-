package com.jfdedit3.bytrolauncher

import android.app.Application

/** Stores a concise, device-local crash report for the next launch. */
class XLocalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val report = buildString {
                    append(throwable.javaClass.name)
                    append(": ")
                    append(throwable.message.orEmpty())
                    append('\n')
                    append(throwable.stackTrace.take(18).joinToString("\n"))
                    throwable.cause?.let { cause ->
                        append("\nCaused by: ").append(cause.javaClass.name)
                        append(": ").append(cause.message.orEmpty())
                    }
                }.take(6000)
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit()
                    .putString("last_crash", report).commit()
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
