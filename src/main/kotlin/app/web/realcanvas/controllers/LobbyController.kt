package app.web.realcanvas.controllers

import app.web.realcanvas.models.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class LobbyController {
    private val lobbies = ConcurrentHashMap<String, Lobby>()
    private val json = Json { encodeDefaults = true }

    suspend fun createLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        val playerMap = mutableMapOf(userName to Player(userName, session))
        lobbies[lobbyId] = Lobby(lobbyId, playerMap)
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[lobbyId],
            gameState = GameState.LOBBY
        )
        session.send(json.encodeToString(returnChange))
        println("Created lobby $lobbies")
    }

    suspend fun onJoinLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        if (!lobbies.containsKey(lobbyId)) {
            println("No lobby with the given ID")
            return
        }
        if (lobbies[lobbyId]?.players?.containsKey(userName) == true) {
            println("Player present")
            return
        }

        lobbies[lobbyId]!!.players[userName] = Player(userName, session)
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[lobbyId],
            gameState = GameState.LOBBY
        )
        sendUpdatedLobbyToAll(lobbies[lobbyId]!!, returnChange)
        println("join $lobbies")
    }

    private suspend fun sendUpdatedLobbyToAll(lobby: Lobby, change: Change) {
        lobby.players.values.forEach {
            it.session?.send(json.encodeToString(change))
        }
    }

    suspend fun tryDisconnect(
        lobbyId: String,
        playerId: String,
        session: WebSocketSession
    ) {
        lobbies[lobbyId]?.players?.get(playerId)?.session?.close(
            CloseReason(CloseReason.Codes.NORMAL, "Finally closed")
        )
        lobbies[lobbyId]?.players?.remove(playerId)

        lobbies[lobbyId].let {
            if (it == null) return
            it.players[playerId]?.session?.close()
            it.players.remove(playerId)
            if (it.players.isEmpty()) {
                println("No players in lobby, removing lobby")
                lobbies.remove(lobbyId)
            }
        }
        session.send("DISCONNECT -> lobbyid = $lobbyId, playerId = $playerId")
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
}