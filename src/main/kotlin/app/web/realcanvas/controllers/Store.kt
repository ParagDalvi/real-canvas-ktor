package app.web.realcanvas.controllers

import app.web.realcanvas.models.Change
import io.ktor.websocket.*

interface Store {
    suspend fun listen(json: String, session: WebSocketSession)
    suspend fun createLobby(change: Change, session: WebSocketSession)
    suspend fun joinLobby(change: Change, session: WebSocketSession)
    suspend fun updateLobby(change: Change)
    suspend fun handleDrawingPoints(change: Change)
    suspend fun handleNewMessage(change: Change)
    suspend fun handleSelectedWord(change: Change)
    suspend fun handleRemovePlayer(change: Change)
}