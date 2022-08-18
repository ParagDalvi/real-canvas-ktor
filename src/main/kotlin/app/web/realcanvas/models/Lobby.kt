package app.web.realcanvas.models

import kotlinx.serialization.Serializable

enum class WhatsHappening {
    WAITING, CHOOSING, DRAWING
}

@Serializable
data class Lobby(
    val id: String,
    val players: MutableMap<String, Player>,
    val messages: MutableList<Message>,
    var whatsHappening: WhatsHappening,
    var timer: Short,
    var word: String
) {
    companion object {
        const val all = "all"
        const val addMessage = "addMessage"
        const val whatsHappening = "whatsHappening"
        const val players = "players"
        const val timer = "timer"
        const val word = "word"
    }
}
