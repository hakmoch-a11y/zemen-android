package com.zemenai.sdk

/**
 * A validated action delivered to the host banking application.
 *
 * The action catalog is configured remotely in the Zemen AI Dashboard.
 * The host app only decides how it wants to handle the route and parameters.
 */
data class ZemenAction(
    val id: String? = null,
    val name: String,
    val route: String,
    val parameters: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap(),
)

fun interface ZemenActionHandler {
    fun handle(action: ZemenAction)
}


/** Recommended single integration boundary for third-party apps. */
fun interface ZemenHostRouter {
    fun open(action: ZemenAction)
}
