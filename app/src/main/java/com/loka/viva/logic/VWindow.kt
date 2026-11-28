package com.loka.viva.logic

data class VWindowGeometry(
    val position: VWindowPosition,
    val size: VWindowSize,
    val windowType: String = "web"
)

data class VWindowPosition(val x: Float, val y: Float, val z: Float) {
    constructor() : this(0f, 0f, -2f)
}

data class VWindowSize(val x: Float, val y: Float) {
    constructor() : this(0.5f, 0.5f)
}

data class VRenderWindow(
    val id: String,
    val position: VWindowPosition,
    val size: VWindowSize,
    val textureId: Int = 0,
    val isActive: Boolean = false
) {
    fun toGeometry(): VWindowGeometry = VWindowGeometry(position, size, "web")
}