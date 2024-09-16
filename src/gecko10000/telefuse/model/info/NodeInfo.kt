package gecko10000.telefuse.model.info

import gecko10000.telefuse.model.Time
import kotlinx.serialization.Serializable

@Serializable
sealed class NodeInfo {
    abstract val name: String
    abstract val permissions: Int
    abstract val uid: Long
    abstract val gid: Long
    abstract val accessTime: Time
    abstract val modificationTime: Time
}
