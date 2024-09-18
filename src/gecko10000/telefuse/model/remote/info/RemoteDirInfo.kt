package gecko10000.telefuse.model.remote.info

import gecko10000.telefuse.model.Time
import kotlinx.serialization.Serializable

@Serializable
data class RemoteDirInfo(
    override val name: String,
    override val permissions: Int,
    override val uid: Long,
    override val gid: Long,
    override val accessTime: Time,
    override val modificationTime: Time,
    val childNodes: Set<RemoteNodeInfo>
) : RemoteNodeInfo()
