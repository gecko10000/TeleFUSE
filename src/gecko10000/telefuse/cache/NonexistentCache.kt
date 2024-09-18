package gecko10000.telefuse.cache

import gecko10000.telefuse.BotManager
import gecko10000.telefuse.IndexManager
import gecko10000.telefuse.model.memory.FileChunk
import gecko10000.telefuse.model.memory.info.DirInfo
import gecko10000.telefuse.model.memory.info.FileInfo
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.ErrorCodes
import ru.serce.jnrfuse.struct.FuseContext
import java.io.FileNotFoundException
import java.util.logging.Logger

class NonexistentCache : IChunkCache, KoinComponent {

    private val log = Logger.getLogger(this::class.qualifiedName)

    private val botManager: BotManager by inject()
    override val indexManager: IndexManager by inject()

    override fun upsertFile(filePath: String, permissions: Int, context: FuseContext) {
        indexManager.updateFileInfo(filePath) {
            val fileInfo = it ?: FileInfo.default(filePath.substringAfterLast('/'), permissions, context)
            fileInfo.copy(permissions = permissions, uid = context.uid.get(), gid = context.gid.get())
        }
        runBlocking { indexManager.flushToRemote() }
    }

    override fun upsertDir(dirPath: String, permissions: Int, context: FuseContext) {
        indexManager.updateDirInfo(dirPath) {
            val dirInfo = it ?: DirInfo.default(dirPath.substringAfterLast('/'), permissions, context)
            dirInfo.copy(permissions = permissions, uid = context.uid.get(), gid = context.gid.get())
        }
        runBlocking { indexManager.flushToRemote() }
    }

    override fun deleteFile(filePath: String) {
        indexManager.updateFileInfo(filePath) { null }
        runBlocking { indexManager.flushToRemote() }
    }

    override fun deleteDir(dirPath: String) {
        indexManager.updateDirInfo(dirPath) { null }
        runBlocking { indexManager.flushToRemote() }
    }

    override fun putChunk(filePath: String, index: Int, chunk: ByteArray?, newSize: Long) {
        indexManager.updateFileInfo(filePath) { info ->
            info ?: run {
                log.warning("putChunk was called on $filePath but info was not found.")
                return@updateFileInfo null
            }
            // Don't allow chunk indices greater than size + 1
            if (index > info.chunks.size) {
                log.warning("putChunk called with index $index but only ${info.chunks.size} found.")
                return@updateFileInfo info
            }
            if (chunk == null) {
                val newFileChunks = info.chunks.filterIndexed { i, _ -> i < index }
                return@updateFileInfo info.copy(chunks = newFileChunks, sizeBytes = newSize)
            }
            val name = "$filePath-$index"
            val newChunk = if (index == info.chunks.size) {
                FileChunk(bytes = chunk, isDirty = true)
            } else {
                info.chunks[index].copy(bytes = chunk, isDirty = true)
            }
            val withAppended = info.chunks.plus(if (index == info.chunks.size) listOf(newChunk) else emptyList())
            val newChunks = withAppended.mapIndexed { i, chunk -> if (index == i) newChunk else chunk }
            info.copy(chunks = newChunks, sizeBytes = newSize)
        }
        runBlocking { indexManager.flushToRemote() }
        // Remove cached chunks. Here at
        // NonexistentCache studios, we
        // do things the hard way.
        indexManager.updateFileInfo(filePath) { info ->
            info ?: throw IllegalArgumentException(
                "File info for $filePath not found despite having been just uploaded."
            )
            val clearedCacheChunks = info.chunks.map { it.copy(bytes = null) }
            info.copy(chunks = clearedCacheChunks)
        }
    }

    override fun getChunk(filePath: String, index: Int): ByteArray {
        val fileInfo = indexManager.getInfo(filePath) as? FileInfo ?: throw FileNotFoundException(filePath)
        val fileChunk = fileInfo.chunks[index]
        return runBlocking { botManager.downloadBytes(fileChunk.fileId!!) }
    }

    /*override fun getChunk(id: FileId): ByteArray {
        return runBlocking { botManager.downloadBytes(id) }
    }*/

    override fun renameNode(oldPath: String, newPath: String): Int {
        val oldInfo = indexManager.getInfo(oldPath)
        oldInfo ?: return -ErrorCodes.ENOENT()
        val newName = newPath.substringAfterLast('/')
        val newInfo = when (oldInfo) {
            is DirInfo -> oldInfo.copy(name = newName)
            is FileInfo -> oldInfo.copy(name = newName)
        }
        indexManager.setInfo(newPath, newInfo)
        indexManager.setInfo(oldPath, null)
        runBlocking { indexManager.flushToRemote() }
        return 0
    }

}
