package gecko10000.telefuse.model

import dev.inmo.tgbotapi.requests.abstracts.FileId
import kotlinx.serialization.Serializable

@Serializable
data class FullFileInfo(
    val sizeBytes: Long,
    val permissions: Int,
    val fileChunks: List<FileId>,
)
