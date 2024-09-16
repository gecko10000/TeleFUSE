package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigWrapper
import gecko10000.telefuse.model.Filesystem
import gecko10000.telefuse.model.info.DirInfo
import gecko10000.telefuse.model.info.FileInfo
import gecko10000.telefuse.model.info.NodeInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.struct.FileStat

class ShardedIndex : KoinComponent {

    private val json: Json by inject()
    private val configFile: JsonConfigWrapper<Config> by inject()
    private val botManager: BotManager by inject()
    private val config: Config
        get() = configFile.value

    private var index: Filesystem = runBlocking { loadIndex() }

    // This cache is used to minimize
    // re-uploads of the index file.
    private var cachedRemoteChunks: List<String> = emptyList()

    private suspend fun loadIndex(): Filesystem = coroutineScope {
        val indexChunks = config.indexFiles
            .map {
                async {
                    botManager.downloadBytes(it)
                }
            }
            .awaitAll()
            .map { it.toString(Charsets.UTF_8) }
        val jsonString = indexChunks.joinToString(separator = "")
        cachedRemoteChunks = indexChunks
        return@coroutineScope if (jsonString.isEmpty()) {
            Filesystem(root = DirInfo.default("/", "644".toInt(8) or FileStat.S_IFDIR, 1000, 1000))
        } else {
            json.decodeFromString(jsonString)
        }
    }

    // Prevents re-uploads of existing files by
    // ensuring the new file does not match the
    // content of the existing file.
    private suspend fun updateIndexChunk(index: Int, newChunk: String): FileId {
        val oldChunk = cachedRemoteChunks.getOrNull(index)
        val previousFileId = config.indexFiles.getOrNull(index)
        if (oldChunk == newChunk && previousFileId != null) return previousFileId
        return botManager.uploadString("~index-$index", newChunk)
    }

    suspend fun saveIndex() = coroutineScope {
        val jsonString = json.encodeToString(index)
        // might need fixing if chunkSize goes over 2G
        val newIndexChunks =
            jsonString.windowed(size = Constant.MAX_CHUNK_SIZE, step = Constant.MAX_CHUNK_SIZE, partialWindows = true)
        val newIndexFiles = newIndexChunks.mapIndexed { i, newChunk ->
            async {
                updateIndexChunk(i, newChunk)
            }
        }.awaitAll()
        configFile.value = config.copy(indexFiles = newIndexFiles)
        configFile.save()
    }

    private tailrec fun getInfo(dirInfo: DirInfo, path: String): NodeInfo? {
        if (path.isEmpty()) return dirInfo
        if (!path.contains('/')) {
            return dirInfo.childNodes
                .firstOrNull { it.name == path }
        }
        val dirName = path.substringBefore('/')
        val childDir = dirInfo.childNodes
            .filterIsInstance<DirInfo>()
            .firstOrNull { it.name == dirName }
            ?: return null
        return getInfo(childDir, path.substringAfter('/'))
    }

    fun getInfo(path: String) = index.root
        ?.let { getInfo(it, path.trimStart('/')) }

    private fun setInfo(dirInfo: DirInfo, path: String, nodeInfo: NodeInfo?): NodeInfo? {
        val newChildren = if (!path.contains('/')) {
            dirInfo.childNodes
                .filterNot { it.name == path }
                .plus(nodeInfo?.let { listOf(nodeInfo) } ?: listOf())
        } else {
            val dirName = path.substringBefore('/')
            val childDir = dirInfo.childNodes
                .filterIsInstance<DirInfo>()
                .firstOrNull { it.name == dirName }
                ?: return null
            val newChildren = setInfo(childDir, path.substringAfter('/'), nodeInfo) ?: return null
            dirInfo.childNodes
                .filterNot { it.name == dirName }
                .plus(newChildren)
        }
        return dirInfo.copy(childNodes = newChildren.toSet())
    }

    fun setInfo(path: String, nodeInfo: NodeInfo?) {
        val updatedRoot = index.root
            ?.let { setInfo(it, path.trimStart('/'), nodeInfo) } as DirInfo?
        index = index.copy(root = updatedRoot)
    }

    fun updateFileInfo(path: String, block: (FileInfo?) -> FileInfo?): Boolean {
        val nodeInfo = getInfo(path)
        if (nodeInfo !is FileInfo?) return false
        val newInfo = block(nodeInfo)
        setInfo(path, newInfo)
        return true
    }

    fun updateDirInfo(path: String, block: (DirInfo?) -> DirInfo?): Boolean {
        val nodeInfo = getInfo(path)
        if (nodeInfo !is DirInfo?) return false
        val newInfo = block(nodeInfo)
        setInfo(path, newInfo)
        return true
    }

}
