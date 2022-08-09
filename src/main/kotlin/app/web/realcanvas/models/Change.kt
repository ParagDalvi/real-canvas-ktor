package app.web.realcanvas.models

import kotlinx.serialization.Serializable

enum class ChangeType {
    CREATE, JOIN, DISCONNECT, LOBBY_UPDATE
}

enum class GameState {
    OUT, LOBBY, IN_GAME
}

@Serializable
data class Change(
    val type: ChangeType,
    val createData: CreateData? = null,
    val joinData: JoinData? = null,
    val disconnectData: DisconnectData? = null,
    val lobbyUpdateData: Lobby? = null,
    val gameState: GameState? = null
)

@Serializable
data class CreateData(
    val userName: String
)

@Serializable
data class JoinData(
    val userName: String,
    val lobbyId: String
)

@Serializable
data class DisconnectData(
    val lobbyId: String,
    val playerId: String
)
