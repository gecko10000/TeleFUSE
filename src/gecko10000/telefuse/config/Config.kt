package gecko10000.telefuse.config

import dev.inmo.tgbotapi.types.ChatIdentifier
import dev.inmo.tgbotapi.types.Username
import gecko10000.telefuse.model.FileMessage
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val token: String = "token_not_set",
    val channelId: ChatIdentifier = Username("@username"),
    val indexFiles: MutableList<FileMessage> = mutableListOf(),
    val requestTimeoutMs: Long = 120000,
)
