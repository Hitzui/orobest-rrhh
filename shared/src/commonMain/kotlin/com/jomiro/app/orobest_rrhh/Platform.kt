package com.jomiro.app.orobest_rrhh

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform