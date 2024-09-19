package gecko10000.telefuse.config

import dev.inmo.tgbotapi.requests.abstracts.FileId
import dev.inmo.tgbotapi.types.ChatIdentifier
import dev.inmo.tgbotapi.types.Username
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val token: String = "token_not_set",
    val channelId: ChatIdentifier = Username("@username"),
    val indexFiles: List<FileId> = listOf(),
    val requestTimeoutMs: Long = 120_000,
    val chunkSizeOverrideBytes: Int? = null,
    val saveIntervalSeconds: Long = 120,
)
