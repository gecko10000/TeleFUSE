package gecko10000.telefuse.model.remote

import gecko10000.telefuse.model.remote.info.RemoteDirInfo
import kotlinx.serialization.Serializable

@Serializable
data class RemoteFS(
    val root: RemoteDirInfo? = null,
)
