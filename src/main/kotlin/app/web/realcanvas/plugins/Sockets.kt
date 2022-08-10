package app.web.realcanvas.plugins

import app.web.realcanvas.Connection
import app.web.realcanvas.controllers.StoreImpl
import app.web.realcanvas.models.Change
import app.web.realcanvas.models.ChangeType
import app.web.realcanvas.models.ErrorData
import app.web.realcanvas.models.GameState
import app.web.realcanvas.utils.RESET
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.*

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
    val store = StoreImpl()
    val json = Json { encodeDefaults = true }

    routing {
        webSocket("/chat") {
            println("Adding user!")
            val thisConnection = Connection(this)
            connections += thisConnection
            try {
                send("You are connected! There are ${connections.count()} users here.")
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    val textWithUsername = "[${thisConnection.name}]: $receivedText"
                    connections.forEach {
                        it.session.send(textWithUsername)
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                println("Removing $thisConnection!")
                connections -= thisConnection
            }
        }

        webSocket("/") {
            try {
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    store.listen(frame.readText(), this)
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
                val returnChange = Change(
                    type = ChangeType.ERROR,
                    gameState = GameState.OUT,
                    errorData = ErrorData(
                        e.message ?: "Unknown Error",
                        "Failed to perform, please try again",
                        "server/Sockets.kt-root",
                        RESET
                    )
                )
                send(json.encodeToString(returnChange))
            } finally {
                println("Force socket close")
                store.forceDisconnect(this)
            }
        }
    }
}
