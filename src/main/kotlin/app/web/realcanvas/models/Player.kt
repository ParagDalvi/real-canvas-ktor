package app.web.realcanvas.models

import io.ktor.websocket.*
import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val userName: String,
    val session: WebSocketSession?
)
