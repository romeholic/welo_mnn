package com.welo.util

import android.util.Log

object LogUtil {
    private const val DEFAULT_TAG = "WELO#"
    private var logLevel = Log.VERBOSE
    private var isLoggable = true
    private var logPrinter: LogPrinter = DefaultLogPrinter()

    interface LogPrinter {
        fun v(tag: String, msg: String)
        fun d(tag: String, msg: String)
        fun i(tag: String, msg: String)
        fun w(tag: String, msg: String)
        fun e(tag: String, msg: String)
        fun wtf(tag: String, msg: String)
    }

    class DefaultLogPrinter : LogPrinter {
        override fun v(tag: String, msg: String) {
            Log.v(tag, msg)
        }

        override fun d(tag: String, msg: String) {
            Log.d(tag, msg)
        }

        override fun i(tag: String, msg: String) {
            Log.i(tag, msg)
        }

        override fun w(tag: String, msg: String) {
            Log.w(tag, msg)
        }

        override fun e(tag: String, msg: String) {
            Log.e(tag, msg)
        }

        override fun wtf(tag: String, msg: String) {
            Log.wtf(tag, msg)
        }
    }

    fun setLogLevel(level: Int) {
        logLevel = level
    }

    fun setLoggable(enable: Boolean) {
        isLoggable = enable
    }

    fun setLogPrinter(printer: LogPrinter?) {
        logPrinter = printer ?: DefaultLogPrinter()
    }

    // VERBOSE 级别日志
    fun v(msg: String) {
        if (isLoggable && logLevel <= Log.VERBOSE) {
            logPrinter.v(DEFAULT_TAG, msg)
        }
    }

    fun v(tag: String, msg: String) {
        if (isLoggable && logLevel <= Log.VERBOSE) {
            logPrinter.v(tag, msg)
        }
    }

    fun v(tag: String, msg: String, tr: Throwable) {
        if (isLoggable && logLevel <= Log.VERBOSE) {
            logPrinter.v(tag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }

    // DEBUG 级别日志
    fun d(msg: String) {
        if (isLoggable && logLevel <= Log.DEBUG) {
            logPrinter.d(DEFAULT_TAG, msg)
        }
    }

    fun d(tag: String, msg: String) {
        if (isLoggable && logLevel <= Log.DEBUG) {
            logPrinter.d(tag, msg)
        }
    }

    fun d(tag: String, msg: String, tr: Throwable) {
        if (isLoggable && logLevel <= Log.DEBUG) {
            logPrinter.d(tag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }

    // INFO 级别日志
    fun i(msg: String) {
        if (isLoggable && logLevel <= Log.INFO) {
            logPrinter.i(DEFAULT_TAG, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (isLoggable && logLevel <= Log.INFO) {
            logPrinter.i(tag, msg)
        }
    }

    fun i(tag: String, msg: String, tr: Throwable) {
        if (isLoggable && logLevel <= Log.INFO) {
            logPrinter.i(tag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }

    // WARN 级别日志
    fun w(msg: String) {
        if (isLoggable && logLevel <= Log.WARN) {
            logPrinter.w(DEFAULT_TAG, msg)
        }
    }

    fun w(tag: String, msg: String) {
        if (isLoggable && logLevel <= Log.WARN) {
            logPrinter.w(tag, msg)
        }
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        if (isLoggable && logLevel <= Log.WARN) {
            logPrinter.w(tag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }

    // ERROR 级别日志
    fun e(msg: String) {
        if (isLoggable && logLevel <= Log.ERROR) {
            logPrinter.e(DEFAULT_TAG, msg)
        }
    }

    fun e(tag: String, msg: String) {
        if (isLoggable && logLevel <= Log.ERROR) {
            logPrinter.e(tag, msg)
        }
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (isLoggable && logLevel <= Log.ERROR) {
            logPrinter.e(tag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }

    // WTF 级别日志
    fun wtf(msg: String) {
        if (isLoggable && logLevel <= Log.ASSERT) {
            logPrinter.wtf(DEFAULT_TAG, msg)
        }
    }

    fun wtf(tag: String, msg: String) {
        if (isLoggable && logLevel <= Log.ASSERT) {
            logPrinter.wtf(tag, msg)
        }
    }

    fun wtf(tag: String, msg: String, tr: Throwable) {
        if (isLoggable && logLevel <= Log.ASSERT) {
            logPrinter.wtf(tag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }
}