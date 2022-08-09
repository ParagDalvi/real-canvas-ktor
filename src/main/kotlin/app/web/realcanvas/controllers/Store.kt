package app.web.realcanvas.controllers

import app.web.realcanvas.models.Change
import io.ktor.websocket.*

interface Store {
    suspend fun listen(json: String, session: WebSocketSession)
    suspend fun createLobby(change: Change)
    suspend fun joinLobby(change: Change)
    suspend fun disconnect(change: Change)
}