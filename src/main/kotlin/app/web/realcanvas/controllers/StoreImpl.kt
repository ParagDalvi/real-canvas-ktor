package app.web.realcanvas.controllers

import app.web.realcanvas.models.Change
import app.web.realcanvas.models.ChangeType
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


class StoreImpl : Store {
    private val lobbyController: LobbyController = LobbyController.instance!!

    override suspend fun listen(json: String, session: WebSocketSession) {
        val change = Json.decodeFromString<Change>(json)
        when (change.type) {
            ChangeType.CREATE -> createLobby(change, session)
            ChangeType.JOIN -> joinLobby(change, session)
            ChangeType.DISCONNECT -> disconnectAndContinue(session)
            ChangeType.LOBBY_UPDATE -> updateLobby(change)
            ChangeType.DRAWING -> handleDrawingPoints(change)
            ChangeType.MESSAGE -> handleNewMessage(change)
            ChangeType.SELECTED_WORD -> handleSelectedWord(change)
            else -> println("Invalid ChangeType")
        }
    }

    override suspend fun createLobby(change: Change, session: WebSocketSession) {
        val userName = change.createData!!.userName
//        val lobbyId = List(6) { ('0'..'9').random() }.joinToString("")
        val lobbyId = "11"
        lobbyController.createLobby(lobbyId, userName, session)
    }

    override suspend fun joinLobby(change: Change, session: WebSocketSession) {
        val userName = change.joinData!!.userName
        val lobbyId = change.joinData.lobbyId
        lobbyController.onJoinLobby(lobbyId, userName, session)
    }

    override suspend fun updateLobby(change: Change) {
        lobbyController.updateLobby(change)
    }

    override suspend fun handleDrawingPoints(change: Change) {
        lobbyController.handleDrawingPoints(change)
    }

    override suspend fun handleNewMessage(change: Change) {
        lobbyController.sendNewMessage(change)
    }

    override suspend fun handleSelectedWord(change: Change) {
        lobbyController.handleSelectedWord(change)
    }

    suspend fun disconnectAndContinue(session: WebSocketSession) {
        lobbyController.disconnectAndContinue(session)
    }
}