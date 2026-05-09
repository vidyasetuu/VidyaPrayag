package com.littlebridge.vidyaprayag

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform