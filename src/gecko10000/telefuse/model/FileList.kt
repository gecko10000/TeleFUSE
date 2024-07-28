package gecko10000.telefuse.model

import kotlinx.serialization.Serializable

@Serializable
data class FileList(
    val files: MutableMap<String, FullFileInfo> = mutableMapOf(),
)
