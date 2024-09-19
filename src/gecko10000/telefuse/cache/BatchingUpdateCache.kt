package gecko10000.telefuse.cache

import gecko10000.telefuse.BotManager
import gecko10000.telefuse.Constant
import gecko10000.telefuse.IndexManager
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigWrapper
import gecko10000.telefuse.model.memory.FileChunk
import gecko10000.telefuse.model.memory.info.DirInfo
import gecko10000.telefuse.model.memory.info.FileInfo
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.ErrorCodes
import ru.serce.jnrfuse.struct.FuseContext
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Logger

class BatchingUpdateCache : IChunkCache, KoinComponent {

    private val log = Logger.getLogger(this::class.qualifiedName)

    override val indexManager: IndexManager by inject()
    private val botManager: BotManager by inject()
    private val configFile: JsonConfigWrapper<Config> by inject()
    private val config: Config
        get() = configFile.value

    private fun syncToRemote() {
        runBlocking { indexManager.flushToRemote() }
        // clear cache after uploading
        for ((path, info) in indexManager.listAllFiles()) {
            val clearedCacheChunks = info.chunks.map { it.copy(bytes = null) }
            val newInfo = info.copy(chunks = clearedCacheChunks)
            indexManager.setInfo(path, newInfo)
        }
    }

    init {
        val task = object : TimerTask() {
            override fun run() {
                syncToRemote()
            }

        }
        Timer().schedule(task, 0, config.saveIntervalSeconds * 1000)
        Runtime.getRuntime().addShutdownHook(Thread { syncToRemote() })
    }

    override fun upsertFile(filePath: String, permissions: Int, context: FuseContext) {
        indexManager.updateFileInfo(filePath) {
            val fileInfo = it ?: FileInfo.default(
                filePath.substringAfterLast('/'),
                permissions,
                context,
                chunkSize = config.chunkSizeOverrideBytes ?: Constant.MAX_CHUNK_SIZE
            )
            fileInfo.copy(permissions = permissions, uid = context.uid.get(), gid = context.gid.get())
        }
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

    override fun putChunk(filePath: String, index: Int, bytes: ByteArray?, newSize: Long) {
        val info = indexManager.getInfo(filePath)
        info ?: run {
            log.warning("putChunk was called on $filePath but info was not found.")
            return
        }
        info as? FileInfo ?: run {
            log.warning("putChunk was called on $filePath but info was of a directory.")
            return
        }
        // Don't allow chunk indices greater than size + 1
        if (index > info.chunks.size) {
            log.warning("putChunk called with index $index but only ${info.chunks.size} found.")
            return
        }
        if (bytes == null) {
            val newFileChunks = info.chunks.filterIndexed { i, _ -> i < index }
            val newInfo = info.copy(chunks = newFileChunks, sizeBytes = newSize)
            indexManager.setInfo(filePath, newInfo)
            return
        }
        if (bytes.size != info.chunkSize) {
            log.warning("putChunk called with a too-small chunk (${bytes.size} instead of ${info.chunkSize}).")
            return
        }
        val oldChunk = info.chunks.getOrNull(index)
        val newChunk = (oldChunk ?: FileChunk(bytes = bytes, isDirty = true))
            .copy(bytes = bytes, isDirty = true)

        val withAppended = info.chunks.plus(if (index == info.chunks.size) listOf(newChunk) else emptyList())
        val newFileChunks = withAppended.mapIndexed { i, chunk -> if (index == i) newChunk else chunk }
        indexManager.setInfo(filePath, info.copy(chunks = newFileChunks, sizeBytes = newSize))
    }

    override fun getChunk(filePath: String, index: Int): ByteArray {
        val info = indexManager.getInfo(filePath)
        info ?: run {
            throw FileNotFoundException("getChunk was called on $filePath but info was not found.")
        }
        info as? FileInfo ?: run {
            throw FileNotFoundException("getChunk was called on $filePath but info was of a directory.")
        }
        if (index >= info.chunks.size) {
            throw FileNotFoundException(
                "getChunk called on $filePath's chunk $index, but file only has ${info.chunks.size}"
            )
        }
        val chunk = info.chunks[index]
        if (chunk.bytes != null) {
            val needsResize = chunk.bytes.size != info.chunkSize
            return if (needsResize) chunk.bytes.copyOf(info.chunkSize) else chunk.bytes
        }
        val bytes = runBlocking { botManager.downloadBytes(chunk.fileId!!) }
        val newChunk = chunk.copy(bytes = bytes)
        val newChunks = info.chunks.mapIndexed { i, c -> if (i == index) newChunk else c }
        val newInfo = info.copy(chunks = newChunks)
        indexManager.setInfo(filePath, newInfo)
        val needsResize = bytes.size != info.chunkSize
        return if (needsResize) bytes.copyOf(info.chunkSize) else bytes
    }

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
        return 0
    }
}
