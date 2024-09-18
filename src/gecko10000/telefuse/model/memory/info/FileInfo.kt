package gecko10000.telefuse.model.memory.info

import gecko10000.telefuse.Constant
import gecko10000.telefuse.model.Time
import gecko10000.telefuse.model.memory.FileChunk
import ru.serce.jnrfuse.struct.FuseContext

data class FileInfo(
    override val name: String,
    override val permissions: Int,
    override val uid: Long,
    override val gid: Long,
    override val accessTime: Time,
    override val modificationTime: Time,
    val sizeBytes: Long,
    val chunkSize: Int,
    val chunks: List<FileChunk>
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
            chunks = emptyList(),
        )
    }
}
