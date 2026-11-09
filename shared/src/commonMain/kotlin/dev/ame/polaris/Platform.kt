package dev.ame.polaris

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform