package app.web.realcanvas.models

import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Player(
    val userName: String,
    var isAdmin: Boolean,
    @Transient
    var session: WebSocketSession? = null,
    var isDrawing: Boolean,
    var score: Int
)
