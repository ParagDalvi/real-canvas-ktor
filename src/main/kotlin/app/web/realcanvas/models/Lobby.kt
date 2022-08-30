package app.web.realcanvas.models

import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    var selectedWord: String,
    val words: MutableList<String>,
    @Transient
    var job: Job? = null
)