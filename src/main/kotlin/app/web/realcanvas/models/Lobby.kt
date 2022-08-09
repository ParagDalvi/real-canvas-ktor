package app.web.realcanvas.models

data class Lobby(
    val id: String,
    val players: MutableMap<String, Player>
)
