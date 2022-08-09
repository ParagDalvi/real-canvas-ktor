package app.web.realcanvas.plugins

import app.web.realcanvas.Connection
import app.web.realcanvas.controllers.StoreImpl
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
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
                    val json = frame.readText()
                    store.listen(json, this)
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                println("Force socket close")
                store.forceDisconnect(this)
            }

        }
    }
}
