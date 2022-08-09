package app.web.realcanvas.models

import kotlinx.serialization.Serializable

@Serializable
data class Lobby(
    val id: String,
    val players: MutableMap<String, Player>
)
