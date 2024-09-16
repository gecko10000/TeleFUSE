package gecko10000.telefuse.cache

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.BotManager
import gecko10000.telefuse.ShardedIndex
import gecko10000.telefuse.model.info.DirInfo
import gecko10000.telefuse.model.info.FileInfo
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
    override val shardedIndex: ShardedIndex = ShardedIndex()

    override fun upsertFile(filePath: String, permissions: Int, context: FuseContext) {
        shardedIndex.updateFileInfo(filePath) {
            val fileInfo = it ?: FileInfo.default(filePath.substringAfterLast('/'), permissions, context)
            fileInfo.copy(permissions = permissions, uid = context.uid.get(), gid = context.gid.get())
        }
        runBlocking { shardedIndex.saveIndex() }
    }

    override fun upsertDir(dirPath: String, permissions: Int, context: FuseContext) {
        shardedIndex.updateDirInfo(dirPath) {
            val dirInfo = it ?: DirInfo.default(dirPath.substringAfterLast('/'), permissions, context)
            dirInfo.copy(permissions = permissions, uid = context.uid.get(), gid = context.gid.get())
        }
        runBlocking { shardedIndex.saveIndex() }
    }

    override fun deleteFile(filePath: String) {
        shardedIndex.updateFileInfo(filePath) { null }
        runBlocking { shardedIndex.saveIndex() }
    }

    override fun deleteDir(dirPath: String) {
        shardedIndex.updateDirInfo(dirPath) { null }
        runBlocking { shardedIndex.saveIndex() }
    }

    override fun putChunk(filePath: String, index: Int, chunk: ByteArray?, newSize: Long) {
        shardedIndex.updateFileInfo(filePath) { info ->
            info ?: run {
                log.warning("putChunk was called on $filePath but info was not found.")
                return@updateFileInfo null
            }
            // Don't allow chunk indices greater than size + 1
            if (index > info.chunkFileIds.size) {
                log.warning("putChunk called with index $index but only ${info.chunkFileIds.size} found.")
                return@updateFileInfo info
            }
            if (chunk == null) {
                val newFileChunks = info.chunkFileIds.filterIndexed { i, _ -> i < index }
                return@updateFileInfo info.copy(chunkFileIds = newFileChunks, sizeBytes = newSize)
            }
            val name = "$filePath-$index"
            val id = runBlocking { botManager.uploadBytes(name, chunk) }
            val withAppended = info.chunkFileIds.plus(if (index == info.chunkFileIds.size) listOf(id) else emptyList())
            val newFileIds = withAppended.mapIndexed { i, fileId -> if (index == i) id else fileId }
            info.copy(chunkFileIds = newFileIds, sizeBytes = newSize)
        }
        runBlocking { shardedIndex.saveIndex() }
    }

    override fun getChunk(filePath: String, index: Int): ByteArray {
        val fileInfo = shardedIndex.getInfo(filePath) as? FileInfo ?: throw FileNotFoundException(filePath)
        val chunkFileId = fileInfo.chunkFileIds[index]
        return runBlocking { botManager.downloadBytes(chunkFileId) }
    }

    override fun getChunk(id: FileId): ByteArray {
        return runBlocking { botManager.downloadBytes(id) }
    }

    override fun renameNode(oldPath: String, newPath: String): Int {
        val oldInfo = shardedIndex.getInfo(oldPath)
        oldInfo ?: return -ErrorCodes.ENOENT()
        val newName = newPath.substringAfterLast('/')
        val newInfo = when (oldInfo) {
            is DirInfo -> oldInfo.copy(name = newName)
            is FileInfo -> oldInfo.copy(name = newName)
        }
        shardedIndex.setInfo(newPath, newInfo)
        shardedIndex.setInfo(oldPath, null)
        runBlocking { shardedIndex.saveIndex() }
        return 0
    }

}
