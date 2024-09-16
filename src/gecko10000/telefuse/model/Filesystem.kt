package gecko10000.telefuse.model

import gecko10000.telefuse.model.info.DirInfo
import kotlinx.serialization.Serializable

@Serializable
data class Filesystem(
    val root: DirInfo? = null
)
