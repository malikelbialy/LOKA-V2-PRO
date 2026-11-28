package com.loka.viva.platform

import com.loka.viva.logic.VWindowPosition

/**
 * A digital window rendered in the mixed-reality scene.
 */
data class VWindow(
    val id: String,
    val title: String,
    val url: String?,
    val position: VWindowPosition,
    val size: Float,
    val windowType: String = "web"
)