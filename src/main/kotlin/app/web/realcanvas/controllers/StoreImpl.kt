package app.web.realcanvas.controllers

import app.web.realcanvas.models.Change
import app.web.realcanvas.models.ChangeType
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


class StoreImpl: Store {
    private var lobbyController: LobbyController = LobbyController()
    private lateinit var session: WebSocketSession

    override suspend fun listen(json: String, session: WebSocketSession) {
        this.session = session
        val change = Json.decodeFromString<Change>(json)
        when(change.type) {
            ChangeType.CREATE -> createLobby(change)
            ChangeType.JOIN -> joinLobby(change)
            ChangeType.DISCONNECT -> disconnect(change)
            else -> println("Invalid ChangeType")
        }
    }

    override suspend fun createLobby(change: Change) {
        val userName = change.createData!!.userName
        val lobbyId = List(6){('0'..'9').random()}.joinToString("")

        lobbyController.createLobby(lobbyId, userName, session)
    }

    override suspend fun joinLobby(change: Change) {
        val userName = change.joinData!!.userName
        val lobbyId = change.joinData.lobbyId
        lobbyController.onJoinLobby(lobbyId, userName, session)
    }

    override suspend fun disconnect(change: Change) {
        lobbyController.tryDisconnect(
            change.disconnectData!!.lobbyId,
            change.disconnectData.playerId,
            session
        )
    }

    suspend fun forceDisconnect(session: WebSocketSession) {
        lobbyController.forceDisconnect(session)
    }

}