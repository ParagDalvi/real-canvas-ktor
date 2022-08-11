package app.web.realcanvas.models

import kotlinx.serialization.Serializable

enum class GameState {
    OUT, LOBBY, IN_GAME
}

@Serializable
data class Lobby(
    val id: String,
    val players: MutableMap<String, Player>,
    val messages: MutableList<Message>,
    val gameState: GameState
)
