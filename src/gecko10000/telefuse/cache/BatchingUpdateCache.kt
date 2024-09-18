package gecko10000.telefuse.cache

import gecko10000.telefuse.BotManager
import gecko10000.telefuse.IndexManager
import gecko10000.telefuse.model.memory.info.DirInfo
import gecko10000.telefuse.model.memory.info.FileInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.struct.FuseContext
import java.util.logging.Logger

class BatchingUpdateCache : IChunkCache, KoinComponent {

    private val log = Logger.getLogger(this::class.qualifiedName)

    private val botManager: BotManager by inject()
    override val indexManager: IndexManager = IndexManager()

    override fun upsertFile(filePath: String, permissions: Int, context: FuseContext) {
        val fileInfo = indexManager.getInfo(filePath)
        /*localIndex[filePath] = fileInfo.copy(
            permissions = permissions,
            uid = context.uid.get(),
            gid = context.gid.get(),
        )*/
    }

    override fun upsertDir(dirPath: String, permissions: Int, context: FuseContext) {
        indexManager.updateDirInfo(dirPath) {
            val dirInfo = it ?: DirInfo.default(dirPath.substringAfterLast('/'), permissions, context)
            dirInfo.copy(permissions = permissions, uid = context.uid.get(), gid = context.gid.get())
        }
    }

    override fun deleteFile(filePath: String) {
        indexManager.updateFileInfo(filePath) { null }
    }

    override fun deleteDir(dirPath: String) {
        indexManager.updateDirInfo(dirPath) { null }
    }

    override fun putChunk(filePath: String, index: Int, chunk: ByteArray?, newSize: Long) {
        val info = indexManager.getInfo(filePath)
        info ?: run {
            log.warning("putChunk was called on $filePath but info was not found.")
            return
        }
        if (info !is FileInfo) run {
            log.warning("putChunk was called on $filePath but info was of a directory.")
            return
        }
        // Don't allow chunk indices greater than size + 1
        if (index > info.chunks.size) {
            log.warning("putChunk called with index $index but only ${info.chunks.size} found.")
            return
        }
        /*if (chunk == null) {
            val newFileChunks = info.chunks.filterIndexed { i, _ -> i < index }
            return info.copy(chunks = newFileChunks, sizeBytes = newSize)
        }
        val name = "$filePath-$index"
        val id = runBlocking { botManager.uploadBytes(name, chunk) }
        val withAppended = info.chunkFileIds.plus(if (index == info.chunkFileIds.size) listOf(id) else emptyList())
        val newFileIds = withAppended.mapIndexed { i, fileId -> if (index == i) id else fileId }
        info.copy(chunkFileIds = newFileIds, sizeBytes = newSize)*/
    }

    override fun getChunk(filePath: String, index: Int): ByteArray {
        TODO("Not yet implemented")
    }

    override fun renameNode(oldPath: String, newPath: String): Int {
        TODO("Not yet implemented")
    }
}
