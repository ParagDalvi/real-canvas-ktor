package app.web.realcanvas.controllers

import app.web.realcanvas.models.*
import app.web.realcanvas.utils.CHOOSING_TIME
import app.web.realcanvas.utils.ONE_SEC
import app.web.realcanvas.utils.TOAST
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class LobbyController {
    private val lobbies = ConcurrentHashMap<String, Lobby>()

    suspend fun createLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        val playerMap = mutableMapOf(userName to Player(userName, true, session, false))
        lobbies[lobbyId] = Lobby(lobbyId, playerMap, mutableListOf(), WhatsHappening.WAITING, 0, "")
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = LobbyUpdateData(Lobby.all, lobbies[lobbyId]!!)
        )
        session.send(Json.encodeToString(returnChange))
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
            session.send(Json.encodeToString(returnChange))
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
            session.send(Json.encodeToString(returnChange))
            return
        }

        lobbies[lobbyId]!!.players[userName] = Player(userName, false, session, false)
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = LobbyUpdateData(Lobby.all, lobbies[lobbyId]!!)
        )
        sendUpdatedLobbyToAll(lobbyId, returnChange)
        println("join $lobbies")
    }

    private suspend fun sendUpdatedLobbyToAll(lobbyId: String, change: Change) {
        lobbies[lobbyId]!!.players.values.forEach {
            // purposely sending from saved player session
            it.session?.send(Json.encodeToString(change))
        }
    }

    suspend fun tryDisconnect(
        lobbyId: String,
        playerId: String,
        session: WebSocketSession
    ) {
        if (lobbies[lobbyId] == null) return

        lobbies[lobbyId]!!.players[playerId]?.session?.close(
            CloseReason(CloseReason.Codes.NORMAL, "Finally closed")
        )
        lobbies[lobbyId]!!.players.remove(playerId)

        // remove lobby if no players
        lobbies[lobbyId].let {
            if (it == null) return
            it.players[playerId]?.session?.close()
            it.players.remove(playerId)
            if (it.players.isEmpty()) {
                println("No players in lobby, removing lobby")
                lobbies.remove(lobbyId)
            }
        }

        lobbies[lobbyId]!!.messages.add(Message(playerId, MessageType.ALERT, "$playerId left"))
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = LobbyUpdateData(Lobby.all, lobbies[lobbyId]!!)
        )
        sendUpdatedLobbyToAll(lobbyId, returnChange)
        println("Player disconnected \n$lobbies")
    }

    suspend fun forceDisconnect(session: WebSocketSession) {
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
        session.send("FORCE DISCONNECT")
        println("force disconnected \n$lobbies")
    }

    suspend fun updateLobby(change: Change) {
        val newLobbyUpdateData = change.lobbyUpdateData!!
        val newLobby = newLobbyUpdateData.data

        if (lobbies[newLobby.id] == null) return

        when (newLobbyUpdateData.updateWhat) {
            Lobby.addMessage -> {
                val newMessage = newLobby.messages.getOrNull(0) ?: return
                lobbies[newLobby.id]!!.messages.add(newMessage)
            }

            Lobby.whatsHappening -> {
                checkAndManageGameStateChange(
                    newLobby.id,
                    lobbies[newLobby.id]!!.whatsHappening,
                    newLobby.whatsHappening
                )
            }
        }

        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = LobbyUpdateData(Lobby.all, lobbies[newLobby.id]!!)
        )
        sendUpdatedLobbyToAll(newLobby.id, returnChange)
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
        val originalPlayers = lobby.players.values.toList() // it may happen that player leaves while playing
        originalPlayers.forEach { currentPlayer ->
            if (lobby.players.containsKey(currentPlayer.userName)) {
                val word = "peace"
                lobby.word = word
                lobby.whatsHappening = WhatsHappening.CHOOSING
                lobby.players.values.forEach { it.isDrawing = currentPlayer.userName == it.userName }
                repeat(CHOOSING_TIME) {
                    lobby.timer = (CHOOSING_TIME - it).toShort()
                    val returnChange = Change(
                        type = ChangeType.LOBBY_UPDATE,
                        lobbyUpdateData = LobbyUpdateData(Lobby.all, lobby)
                    )
                    sendUpdatedLobbyToAll(lobby.id, returnChange)
                    delay(ONE_SEC)
                }

                lobby.whatsHappening = WhatsHappening.DRAWING
                repeat(30) {
                    lobby.timer = (30 - it).toShort()
                    val returnChange = Change(
                        type = ChangeType.LOBBY_UPDATE,
                        lobbyUpdateData = LobbyUpdateData(Lobby.all, lobby)
                    )
                    sendUpdatedLobbyToAll(lobby.id, returnChange)
                    delay(ONE_SEC)
                }
            }
        }
    }

    private fun addExistingPlayerSessions(newLobby: Lobby) {
        newLobby.players.values.forEach {
            it.session = lobbies[newLobby.id]!!.players[it.userName]?.session
        }
    }
}