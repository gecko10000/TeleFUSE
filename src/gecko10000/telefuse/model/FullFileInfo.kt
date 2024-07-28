package gecko10000.telefuse.model

import kotlinx.serialization.Serializable

@Serializable
data class FullFileInfo(
    val sizeBytes: Long,
    val permissions: Int,
    val fileMessages: List<FileMessage>,
)
