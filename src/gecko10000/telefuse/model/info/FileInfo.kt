package gecko10000.telefuse.model.info

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.Constant
import gecko10000.telefuse.model.Time
import kotlinx.serialization.Serializable
import ru.serce.jnrfuse.struct.FuseContext

@Serializable
data class FileInfo(
    override val name: String,
    override val permissions: Int,
    override val uid: Long,
    override val gid: Long,
    override val accessTime: Time,
    override val modificationTime: Time,
    val sizeBytes: Long,
    val chunkSize: Int,
    val chunkFileIds: List<FileId>
) : NodeInfo() {
    companion object {
        fun default(
            name: String,
            permissions: Int,
            context: FuseContext,
            chunkSize: Int = Constant.MAX_CHUNK_SIZE
        ) = FileInfo(
            name = name,
            permissions = permissions,
            uid = context.uid.get(),
            gid = context.gid.get(),
            accessTime = Time.now(),
            modificationTime = Time.now(),
            sizeBytes = 0,
            chunkSize = chunkSize,
            chunkFileIds = emptyList(),
        )
    }
}
