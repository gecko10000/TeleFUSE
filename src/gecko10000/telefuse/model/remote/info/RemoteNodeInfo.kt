package gecko10000.telefuse.model.remote.info

import gecko10000.telefuse.model.Time
import kotlinx.serialization.Serializable

@Serializable
sealed class RemoteNodeInfo {
    abstract val name: String
    abstract val permissions: Int
    abstract val uid: Long
    abstract val gid: Long
    abstract val accessTime: Time
    abstract val modificationTime: Time
}
