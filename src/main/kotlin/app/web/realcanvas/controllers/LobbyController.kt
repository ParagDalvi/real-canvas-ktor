package app.web.realcanvas.controllers

import app.web.realcanvas.models.*
import app.web.realcanvas.utils.CHOOSING_TIME
import app.web.realcanvas.utils.ONE_SEC
import app.web.realcanvas.utils.TOAST
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class LobbyController {
    private val lobbies = ConcurrentHashMap<String, Lobby>()

    suspend fun createLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        val playerMap = mutableMapOf(userName to Player(userName, true, session, false, 0))
        lobbies[lobbyId] = Lobby(lobbyId, playerMap, mutableListOf(), WhatsHappening.WAITING, 0, "")
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[lobbyId]
        )
        sendData(session, returnChange)
        println("Created lobby $lobbies")
    }

    suspend fun onJoinLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        if (!lobbies.containsKey(lobbyId)) {
            println("No lobby with the given ID")
            val returnChange = Change(
                type = ChangeType.ERROR,
                errorData = ErrorData(
                    "No lobby found, check code",
                    "No lobby found, check code",
                    "server/Sockets.kt-onJoinLobby",
                    doWhat = TOAST
                )
            )
            sendData(session, returnChange)
            return
        }
        if (lobbies[lobbyId]?.players?.containsKey(userName) == true) {
            println("Player present")
            val returnChange = Change(
                type = ChangeType.ERROR,
                errorData = ErrorData(
                    "Same Username present, try different",
                    "Same Username present, try different",
                    "server/Sockets.kt-onJoinLobby",
                    doWhat = TOAST
                )
            )
            sendData(session, returnChange)
            return
        }

        lobbies[lobbyId]!!.players[userName] = Player(userName, false, session, false, 0)
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[lobbyId]!!
        )
        sendUpdatedLobbyToAll(lobbyId, returnChange)
        println("join $lobbies")
    }

    private suspend fun sendUpdatedLobbyToAll(lobbyId: String, change: Change, except: String? = null) {
        CoroutineScope(coroutineContext).launch {
            lobbies[lobbyId]!!.players.values.forEach {
                // purposely sending from saved player session
                if (it.userName != except)
                    it.session?.send(Json.encodeToString(change))
            }
        }
    }

    suspend fun tryDisconnect(
        lobbyId: String,
        playerId: String,
    ) {
        if (lobbies[lobbyId] == null) return

        closeSession(
            lobbies[lobbyId]!!.players[playerId]?.session,
            CloseReason(CloseReason.Codes.NORMAL, "Finally closed")
        )
        lobbies[lobbyId]!!.players.remove(playerId)

        // remove lobby if no players
        lobbies[lobbyId].let {
            if (it == null) return
            it.players.remove(playerId)
            if (it.players.isEmpty()) {
                println("No players in lobby, removing lobby")
                lobbies.remove(lobbyId)
            }
        }

        lobbies[lobbyId]!!.messages.clear()
        lobbies[lobbyId]!!.messages.add(Message(playerId, MessageType.ALERT, "$playerId left"))
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[lobbyId]!!
        )
        sendUpdatedLobbyToAll(lobbyId, returnChange)
        println("Player disconnected \n$lobbies")
    }

    private suspend fun closeSession(session: WebSocketSession?, closeReason: CloseReason) {
        CoroutineScope(coroutineContext).launch {
            session?.close(closeReason)
        }
    }

    fun forceDisconnect(session: WebSocketSession) {
        var removeLobbyIfNoPlayers = "FALSE"
        lobbies.values.forEach { lobby ->
            val removed = lobby.players.entries.removeIf { it.value.session == session }
            if (lobby.players.isEmpty()) {
                removeLobbyIfNoPlayers = lobby.id
            }
            if (removed) return@forEach
        }
        if (removeLobbyIfNoPlayers != "FALSE") {
            println("No players in lobby, removing lobby")
            lobbies.remove(removeLobbyIfNoPlayers)
        }
        println("force disconnected \n$lobbies")
    }

    suspend fun updateLobby(change: Change) {
        val newLobby = change.lobbyUpdateData!!

        if (lobbies[newLobby.id] == null) return

        val previousGameState = lobbies[newLobby.id]!!.whatsHappening
        addExistingPlayerSessions(newLobby)
        lobbies[newLobby.id] = newLobby

        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[newLobby.id]!!
        )

        sendUpdatedLobbyToAll(newLobby.id, returnChange)

        checkAndManageGameStateChange(
            newLobby.id,
            previousGameState,
            newLobby.whatsHappening
        )
    }

    private fun addExistingPlayerSessions(newLobby: Lobby) {
        newLobby.players.values.forEach {
            it.session = lobbies[newLobby.id]!!.players[it.userName]?.session
        }
    }

    private suspend fun checkAndManageGameStateChange(
        lobbyId: String,
        previousGameState: WhatsHappening?,
        newGameState: WhatsHappening
    ) {
        val lobby = lobbies[lobbyId]
        if (previousGameState == null || lobby == null) return

        if (previousGameState == WhatsHappening.WAITING && newGameState == WhatsHappening.CHOOSING) {
            // start game
            startGame(lobby)
        }
    }

    private suspend fun startGame(lobby: Lobby) {
        CoroutineScope(coroutineContext).launch {
            val originalPlayers = lobby.players.values.toList()
            originalPlayers.forEach { currentPlayer ->
                if (lobby.players.containsKey(currentPlayer.userName)) {
                    val word = "peace"
                    lobby.word = word
                    lobby.whatsHappening = WhatsHappening.CHOOSING
                    lobby.players.values.forEach { it.isDrawing = currentPlayer.userName == it.userName }

                    sendUpdatedLobbyToAll(
                        lobby.id,
                        Change(type = ChangeType.LOBBY_UPDATE, lobbyUpdateData = lobby)
                    )

                    repeat(CHOOSING_TIME) {
                        lobby.timer = (CHOOSING_TIME - it).toShort()
                        val returnChange = Change(
                            type = ChangeType.LOBBY_UPDATE,
                            lobbyUpdateData = lobby
                        )
                        sendUpdatedLobbyToAll(lobby.id, returnChange)
                        delay(ONE_SEC)
                    }

                    lobby.whatsHappening = WhatsHappening.DRAWING
                    repeat(2) {
                        lobby.timer = (2 - it).toShort()
                        val returnChange = Change(
                            type = ChangeType.LOBBY_UPDATE,
                            lobbyUpdateData = lobby
                        )
                        sendUpdatedLobbyToAll(lobby.id, returnChange)
                        delay(ONE_SEC)
                    }
                }
            }
            lobby.whatsHappening = WhatsHappening.WAITING
            val returnChange = Change(
                type = ChangeType.LOBBY_UPDATE,
                lobbyUpdateData = lobby
            )
            lobby.players.values.forEach { it.isDrawing = false }
            sendUpdatedLobbyToAll(lobby.id, returnChange)
        }
    }

    suspend fun handleDrawingPoints(change: Change) {
        val data = change.drawingData!!
        sendUpdatedLobbyToAll(data.lobbyId, change, data.userName)
    }

    private suspend fun sendData(session: WebSocketSession, change: Change) {
        CoroutineScope(coroutineContext).launch {
            session.send(Json.encodeToString(change))
        }
    }

    suspend fun sendNewMessage(change: Change) {
        val lobbyId = change.messageData!!.lobbyId
        sendUpdatedLobbyToAll(lobbyId, change)
    }
}