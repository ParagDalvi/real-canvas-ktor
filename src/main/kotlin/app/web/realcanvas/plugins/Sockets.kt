package app.web.realcanvas.plugins

import app.web.realcanvas.controllers.StoreImpl
import app.web.realcanvas.models.Change
import app.web.realcanvas.models.ChangeType
import app.web.realcanvas.models.ErrorData
import app.web.realcanvas.utils.RESET
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    val store = StoreImpl()
    val json = Json { encodeDefaults = true }

    routing {
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
                store.disconnectAndContinue(this)
            }
        }
    }
}
