package app.web.realcanvas

import app.web.realcanvas.plugins.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureSockets()
//        configureMonitoring()
//        configureSecurity()
//        configureRouting()
    }.start(wait = true)
}
