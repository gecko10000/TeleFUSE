package gecko10000.telefuse.model

import dev.inmo.tgbotapi.requests.abstracts.FileId
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.serialization.Serializable

@Serializable
data class FileMessage(
    val messageId: MessageId,
    val fileId: FileId,
)
