package gecko10000.telefuse.model

import kotlinx.serialization.Serializable

@Serializable
data class FileList(
    val files: Map<String, FullFileInfo> = mapOf(),
)
