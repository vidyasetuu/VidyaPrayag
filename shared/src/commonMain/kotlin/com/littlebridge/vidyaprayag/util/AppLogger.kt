package com.littlebridge.vidyaprayag.util

expect object AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
