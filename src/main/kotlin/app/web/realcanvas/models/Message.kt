package app.web.realcanvas.models

import kotlinx.serialization.Serializable

enum class MessageType {
    ALERT, DEFAULT
}

@Serializable
data class Message(
    val userName: String,
    val type: MessageType,
    val message: String
)
