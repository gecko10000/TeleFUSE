package gecko10000.telefuse.cache

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.ShardedIndex
import ru.serce.jnrfuse.struct.FuseContext

interface IChunkCache {

    val shardedIndex: ShardedIndex

    // Intended ONLY to update the file information
    // NOT the contents
    fun upsertFile(filePath: String, permissions: Int, context: FuseContext)

    // Updates directory information
    fun upsertDir(dirPath: String, permissions: Int, context: FuseContext)

    // Completely deletes node
    fun deleteFile(filePath: String)
    fun deleteDir(dirPath: String)

    // Replaces the chunk at index `index` with the content given.
    // If `chunk` is null, the chunk is deleted.
    fun putChunk(filePath: String, index: Int, chunk: ByteArray?, newSize: Long)

    // Retrieves the chunk at index `index`.
    fun getChunk(filePath: String, index: Int): ByteArray

    fun getChunk(id: FileId): ByteArray

    fun renameNode(oldPath: String, newPath: String): Int

}