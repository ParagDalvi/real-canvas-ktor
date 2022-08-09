package app.web.realcanvas.models

import kotlinx.serialization.Serializable

enum class ChangeType {
    CREATE, JOIN, DISCONNECT
}

@Serializable
data class Change(
    val type: ChangeType,
    val createData: CreateData?,
    val joinData: JoinData?,
    val disconnectData: DisconnectData?
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
