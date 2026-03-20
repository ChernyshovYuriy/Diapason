package com.yuriy.diapason.logging

import android.util.Log

object AppLogger {

    private const val LOG_TAG = "Diapason:"

    private var sDebug = false

    fun setDebug(value: Boolean) {
        sDebug = value
    }

    @JvmStatic
    fun e(logMsg: String) {
        if (sDebug) {
            Log.e(LOG_TAG, "[${getThreadName()}] $logMsg")
        }
    }

    @JvmStatic
    fun e(logMsg: String, t: Throwable?) {
        if (sDebug) {
            Log.e(LOG_TAG,"[${getThreadName()}] $logMsg", t)
        }
    }

    @JvmStatic
    fun w(logMsg: String) {
        if (sDebug) {
            Log.w(LOG_TAG, "[${getThreadName()}] $logMsg")
        }
    }

    @JvmStatic
    fun i(logMsg: String) {
        if (sDebug) {
            Log.i(LOG_TAG, "[${getThreadName()}] $logMsg")
        }
    }

    @JvmStatic
    fun d(logMsg: String) {
        if (sDebug) {
            Log.d(LOG_TAG, "[${getThreadName()}] $logMsg")
        }
    }

    private fun getThreadName(): String {
        return Thread.currentThread().name
    }
}