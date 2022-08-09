package app.web.realcanvas.controllers

import app.web.realcanvas.models.Lobby
import app.web.realcanvas.models.Player
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

class LobbyController {
    private val lobbies = ConcurrentHashMap<String, Lobby>()

    suspend fun createLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        if(lobbies.containsKey(lobbyId)) {
            println("Lobby already present")
            return
        }
        val playerMap = mutableMapOf(userName to Player(userName, session))
        lobbies[lobbyId] = Lobby(lobbyId, playerMap)
        session.send("CREATE ___ lobbyid = $lobbyId, userName = $userName")
        println("create $lobbies")
    }

    suspend fun onJoinLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession
    ) {
        if(!lobbies.containsKey(lobbyId)) {
            println("No lobby with the given ID")
            return
        }
        if(lobbies[lobbyId]?.players?.containsKey(userName) == true) {
            println("Player present")
            return
        }

        lobbies[lobbyId]?.players?.set(userName, Player(userName, session))
        session.send("JOIN ____ lobbyid = $lobbyId, userName = $userName")
        println("join $lobbies")
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
            if(it == null) return
            it.players[playerId]?.session?.close()
            it.players.remove(playerId)
            if(it.players.isEmpty()) {
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
            if(lobby.players.isEmpty()) {
                removeLobbyIfNoPlayers = lobby.id
            }
            if(removed) return@forEach
        }
        if(removeLobbyIfNoPlayers != "FALSE") {
            println("No players in lobby, removing lobby")
            lobbies.remove(removeLobbyIfNoPlayers)
        }
        session.send("FORCE DISCONNECT")
        println("force disconnected \n$lobbies")
    }
}