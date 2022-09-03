package app.web.realcanvas.controllers

import app.web.realcanvas.models.*
import app.web.realcanvas.utils.CHOOSING_TIME
import app.web.realcanvas.utils.ONE_SEC
import app.web.realcanvas.utils.TOAST
import app.web.realcanvas.utils.WORDS
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class LobbyController {
    private val lobbies = ConcurrentHashMap<String, Lobby>()
    private val allSessions = ConcurrentHashMap<WebSocketSession, Pair<String, String>>()

    companion object {
        var instance: LobbyController? = null
            get() {
                if (field == null)
                    instance = LobbyController()
                return field
            }
    }

    suspend fun createLobby(
        lobbyId: String,
        userName: String,
        session: WebSocketSession,
    ) {
        val playerMap = mutableMapOf(userName to Player(userName, true, session, false, 0))
        lobbies[lobbyId] = Lobby(lobbyId, playerMap, mutableListOf(), WhatsHappening.WAITING, 0, mutableListOf())
        allSessions[session] = Pair(lobbyId, userName)
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
        session: WebSocketSession,
    ) {
        if (!lobbies.containsKey(lobbyId)) {
            println("No lobby with the given ID")
            val returnChange = Change(
                //todo: by these errors, in viewModel connection is already made, can be optimised
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
                //todo: by these errors, in viewModel connection is already made, can be optimised
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

        if (lobbies[lobbyId]?.whatsHappening != WhatsHappening.WAITING) {
            //todo: by these errors, in viewModel connection is already made, can be optimised
            println("Game already started")
            val returnChange = Change(
                type = ChangeType.ERROR,
                errorData = ErrorData(
                    "Game already started, please wait for it to finish",
                    "Game already started, please wait for it to finish",
                    "server/Sockets.kt-onJoinLobby",
                    doWhat = TOAST
                )
            )
            sendData(session, returnChange)
            return
        }

        lobbies[lobbyId]!!.players[userName] = Player(userName, false, session, false, 0)
        allSessions[session] = Pair(lobbyId, userName)
        val returnChange = Change(
            type = ChangeType.LOBBY_UPDATE,
            lobbyUpdateData = lobbies[lobbyId]!!
        )
        sendUpdatedLobbyToAll(lobbyId, returnChange)
        println("join $lobbies")
    }

    private suspend fun sendUpdatedLobbyToAll(lobbyId: String, change: Change, except: String? = null) {
        CoroutineScope(Dispatchers.Default).launch {
            lobbies[lobbyId]?.players?.values?.forEach {
                // purposely sending from saved player session
                if (it.userName != except && it.session?.isActive == true)
                    it.session?.send(Json.encodeToString(change))
            }
        }
    }

    private suspend fun closeSession(session: WebSocketSession?, closeReason: CloseReason? = null) {
        CoroutineScope(Dispatchers.Default).launch {
            if (closeReason != null) {
                session?.close(closeReason)
            }
        }
    }

    suspend fun disconnectAndContinue(session: WebSocketSession) {
        if (!allSessions.containsKey(session)) return

        closeSession(session)

        val lobbyId = allSessions[session]!!.first
        val username = allSessions[session]!!.second

        allSessions.remove(session)

        val leftPlayer = lobbies[lobbyId]?.players?.get(username)
        var nextPlayerIndex = -1
        if (leftPlayer?.isDrawing == true) {
            lobbies[lobbyId]?.players?.values?.forEachIndexed { ind, player ->
                if (player == leftPlayer) {
                    nextPlayerIndex = ind
                }
            }
        }

        lobbies[lobbyId]?.players?.remove(username)
        if (lobbies[lobbyId]?.players?.isEmpty() == true) {
            lobbies.remove(lobbyId)
            return
        }
        var returnChange = Change(
            ChangeType.MESSAGE,
            messageData = MessageData(lobbyId, message = Message(username, MessageType.DEFAULT, "$username left"))
        )
        sendUpdatedLobbyToAll(lobbyId, returnChange)

        if (leftPlayer?.isAdmin == true)
            lobbies[lobbyId]?.players?.values?.first()?.isAdmin = true

        if (lobbies[lobbyId]?.whatsHappening == WhatsHappening.WAITING) {
            returnChange = Change(ChangeType.LOBBY_UPDATE, lobbyUpdateData = lobbies[lobbyId])
            sendUpdatedLobbyToAll(lobbyId, returnChange)
            return
        }

        continueGame(lobbyId, nextPlayerIndex)
    }

    suspend fun updateLobby(change: Change) {
        val newLobby = change.lobbyUpdateData!!

        if (lobbies[newLobby.id] == null) return

        val previousGameState = lobbies[newLobby.id]!!.whatsHappening
        addExistingPlayerSessions(newLobby)
        newLobby.job = lobbies[newLobby.id]?.job
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
            startGame(lobbyId)
        }
    }

    private suspend fun startGame(lobbyId: String, nextPlayerIndex: Int = 0) {
        lobbies[lobbyId]!!.job?.cancelAndJoin()
        lobbies[lobbyId]!!.job = CoroutineScope(Dispatchers.Default).launch {
            val totalPlayers = lobbies[lobbyId]!!.players.size
            val originalPlayers = lobbies[lobbyId]!!.players.values.toList().subList(nextPlayerIndex, totalPlayers)
            originalPlayers.forEach { currentPlayer ->
                if (lobbies[lobbyId]!!.players.containsKey(currentPlayer.userName)) {
                    setRandomWords(lobbyId)
                    lobbies[lobbyId]!!.whatsHappening = WhatsHappening.CHOOSING
                    lobbies[lobbyId]!!.players.values.forEach {
                        it.isDrawing = currentPlayer.userName == it.userName
                    }

                    sendUpdatedLobbyToAll(
                        lobbies[lobbyId]!!.id,
                        Change(type = ChangeType.LOBBY_UPDATE, lobbyUpdateData = lobbies[lobbyId]!!)
                    )

                    repeat(CHOOSING_TIME) {
                        lobbies[lobbyId]!!.timer = (CHOOSING_TIME - it).toShort()
                        val returnChange = Change(
                            type = ChangeType.LOBBY_UPDATE,
                            lobbyUpdateData = lobbies[lobbyId]!!
                        )
                        sendUpdatedLobbyToAll(lobbies[lobbyId]!!.id, returnChange)
                        delay(ONE_SEC)
                    }

                    lobbies[lobbyId]!!.whatsHappening = WhatsHappening.DRAWING
                    repeat(30) {
                        lobbies[lobbyId]!!.timer = (30 - it).toShort()
                        val returnChange = Change(
                            type = ChangeType.LOBBY_UPDATE,
                            lobbyUpdateData = lobbies[lobbyId]!!
                        )
                        sendUpdatedLobbyToAll(lobbies[lobbyId]!!.id, returnChange)
                        delay(ONE_SEC)
                    }

                    // wait to show correct word ui
                    repeat(5) { delay(ONE_SEC) }
                }
            }
            lobbies[lobbyId]!!.whatsHappening = WhatsHappening.WAITING
            lobbies[lobbyId]!!.players.values.forEach { it.isDrawing = false }
            val returnChange = Change(
                type = ChangeType.LOBBY_UPDATE,
                lobbyUpdateData = lobbies[lobbyId]!!
            )
            sendUpdatedLobbyToAll(lobbies[lobbyId]!!.id, returnChange)
        }
    }

    private suspend fun setRandomWords(lobbyId: String) {
        val randomIndexes = List(3) { (WORDS.indices).random() }
        with(lobbies[lobbyId]) {
            if (this == null) return
            words.clear()
            randomIndexes.forEach { words.add(WORDS[it]) }
            sendUpdatedLobbyToAll(
                id,
                Change(ChangeType.SELECTED_WORD, selectedWordData = SelectedWordData(id, words[0]))
            )
        }
    }

    suspend fun handleDrawingPoints(change: Change) {
        val data = change.drawingData!!
        sendUpdatedLobbyToAll(data.lobbyId, change, data.userName)
    }

    private suspend fun sendData(session: WebSocketSession, change: Change) {
        CoroutineScope(Dispatchers.Default).launch {
            if (session.isActive)
                session.send(Json.encodeToString(change))
        }
    }

    suspend fun sendNewMessage(change: Change) {
        val lobbyId = change.messageData!!.lobbyId
        if (lobbies[lobbyId]?.whatsHappening == WhatsHappening.DRAWING && change.messageData.message.message == change.messageData.selectedWord) {
            val playerId = change.messageData.playerId
            if (playerId != null && lobbies.containsKey(lobbyId) && lobbies[lobbyId]!!.players.containsKey(playerId)) {
                lobbies[lobbyId]!!.players[playerId]!!.score += 10
                val successChangeMessage = Change(
                    ChangeType.MESSAGE, messageData = MessageData(
                        lobbyId,
                        message = Message(playerId, MessageType.GUESS_SUCCESS, "$playerId is correct")
                    )
                )
                sendUpdatedLobbyToAll(lobbyId, successChangeMessage)
            }
        } else {
            sendUpdatedLobbyToAll(lobbyId, change)
        }
    }

    private suspend fun continueGame(lobbyId: String, nextPlayerIndex: Int) {
        if (!lobbies.containsKey(lobbyId) || nextPlayerIndex == -1) return

        // (left player is the last player) || (if only 1 player left in lobby), no need to continue
        if (nextPlayerIndex > (lobbies[lobbyId]?.players?.size ?: Integer.MIN_VALUE)
            || lobbies[lobbyId]?.players?.size == 1
        ) {
            lobbies[lobbyId]!!.job?.cancelAndJoin()
            lobbies[lobbyId]!!.whatsHappening = WhatsHappening.WAITING
            val returnChange = Change(type = ChangeType.LOBBY_UPDATE, lobbyUpdateData = lobbies[lobbyId]!!)
            sendUpdatedLobbyToAll(lobbyId, returnChange)
            return
        } else {
            startGame(lobbyId, nextPlayerIndex)
        }
    }

    suspend fun handleSelectedWord(change: Change) {
        val selectedWordData = change.selectedWordData!!
        sendUpdatedLobbyToAll(selectedWordData.lobbyId, change)
    }
}