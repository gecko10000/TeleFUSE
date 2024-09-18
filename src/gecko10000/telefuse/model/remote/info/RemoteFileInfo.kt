package gecko10000.telefuse.model.remote.info

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.model.Time
import kotlinx.serialization.Serializable

@Serializable
data class RemoteFileInfo(
    override val name: String,
    override val permissions: Int,
    override val uid: Long,
    override val gid: Long,
    override val accessTime: Time,
    override val modificationTime: Time,
    val sizeBytes: Long,
    val chunkSize: Int,
    val chunkFileIds: List<FileId>
) : RemoteNodeInfo()
