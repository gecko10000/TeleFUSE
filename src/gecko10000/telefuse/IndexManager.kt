package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.model.memory.FileChunk
import gecko10000.telefuse.model.memory.FileSystem
import gecko10000.telefuse.model.memory.info.DirInfo
import gecko10000.telefuse.model.memory.info.FileInfo
import gecko10000.telefuse.model.memory.info.NodeInfo
import gecko10000.telefuse.model.remote.RemoteFS
import gecko10000.telefuse.model.remote.info.RemoteDirInfo
import gecko10000.telefuse.model.remote.info.RemoteFileInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.struct.FileStat
import java.util.logging.Logger
import kotlin.math.round
import kotlin.system.measureTimeMillis

class IndexManager : KoinComponent {

    private val log = Logger.getLogger(this::class.qualifiedName)

    private val remoteIndexManager: RemoteIndexManager by inject()
    private val botManager: BotManager by inject()

    private var index: FileSystem = syncFromRemote()

    // Only used at startup time, to load
    // remote filesystem into memory representation
    private fun syncFromRemote(): FileSystem {
        val root = remoteIndexManager.index.root
        root ?: return FileSystem(
            root = DirInfo.default(
                name = "/",
                permissions = "644".toInt(8) or FileStat.S_IFDIR,
                uid = 1000,
                gid = 1000,
            )
        )
        return FileSystem(root = remoteToDir(root))
    }

    private fun remoteToFile(remote: RemoteFileInfo): FileInfo {
        return FileInfo(
            name = remote.name,
            permissions = remote.permissions,
            uid = remote.uid,
            gid = remote.gid,
            accessTime = remote.accessTime,
            modificationTime = remote.modificationTime,
            sizeBytes = remote.sizeBytes,
            chunkSize = remote.chunkSize,
            chunks = remote.chunkFileIds.map { FileChunk(fileId = it) }
        )
    }

    private fun remoteToDir(remote: RemoteDirInfo): DirInfo {
        val children = remote.childNodes.map {
            when (it) {
                is RemoteDirInfo -> remoteToDir(it)
                is RemoteFileInfo -> remoteToFile(it)
            }
        }.toSet()
        return DirInfo(
            name = remote.name,
            permissions = remote.permissions,
            uid = remote.uid,
            gid = remote.gid,
            accessTime = remote.accessTime,
            modificationTime = remote.modificationTime,
            childNodes = children
        )
    }

    // Uploads file chunks to remote
    // and updates/saves index.
    // This will also set all `isDirty`
    // fields in the file chunks to false.
    // Make sure to re-retrieve the FileInfos.
    // TODO: use mutex for this and prevent any FUSE operations while locked
    suspend fun flushToRemote() {
        log.info("Starting flush to remote.")
        val timeTakenMs = measureTimeMillis {
            // sync all needed file chunks
            val root = uploadFileChunks(index.root)
            // update root locally
            index = index.copy(root = root)
            // convert index to remote index
            val remoteRoot = dirToRemote(root)
            // set remote index
            remoteIndexManager.index = RemoteFS(remoteRoot)
            // save index to Telegram
            remoteIndexManager.saveIndex()
            // profit?
        }
        val seconds = round(timeTakenMs.toDouble() / 1000).toLong()
        log.info("Finished flushing to remote in $seconds seconds.")
    }
    /*
        val updatedChunks = file.chunks.mapIndexed { i, chunk ->
            chunk.copy(fileId = fileIds[i], isDirty = false)
        }
        setInfo(parent, file.name, file.copy(chunks = updatedChunks))
     */

    // Validation is done upon chunk
    // creation, so there should not be
    // an NPE here ever™.
    private suspend fun syncChunk(path: String, file: FileInfo, chunkIndex: Int): FileId {
        val chunk = file.chunks[chunkIndex]
        if (chunk.isDirty) {
            return botManager.uploadBytes(path, chunk.bytes!!)
        }
        return chunk.fileId!!
    }

    private suspend fun uploadFileChunks(file: FileInfo): FileInfo = coroutineScope {
        val fileIds = IntRange(0, file.chunks.size - 1).map { i ->
            async {
                syncChunk(file.name, file, i)
            }
        }.awaitAll()
        val newChunks = file.chunks.mapIndexed { i, chunk ->
            chunk.copy(fileId = fileIds[i], isDirty = false)
        }
        return@coroutineScope file.copy(chunks = newChunks)
    }

    private suspend fun uploadFileChunks(dir: DirInfo): DirInfo = coroutineScope {
        val newChildren = dir.childNodes.map {
            async {
                when (it) {
                    is DirInfo -> uploadFileChunks(it)
                    is FileInfo -> uploadFileChunks(it)
                }
            }
        }.awaitAll().toSet()
        return@coroutineScope dir.copy(childNodes = newChildren)
    }

    private fun dirToRemote(dir: DirInfo): RemoteDirInfo {
        val children = dir.childNodes.map {
            when (it) {
                is DirInfo -> dirToRemote(it)
                is FileInfo -> fileToRemote(it)
            }
        }
        return RemoteDirInfo(
            name = dir.name,
            permissions = dir.permissions,
            uid = dir.uid,
            gid = dir.gid,
            accessTime = dir.accessTime,
            modificationTime = dir.modificationTime,
            childNodes = children.toSet()
        )
    }

    private fun fileToRemote(file: FileInfo): RemoteFileInfo {
        val extractedIds = file.chunks.map {
            it.fileId ?: throw IllegalArgumentException("fileToRemote called without file ID.")
        }
        return RemoteFileInfo(
            name = file.name,
            permissions = file.permissions,
            uid = file.uid,
            gid = file.gid,
            accessTime = file.accessTime,
            modificationTime = file.modificationTime,
            sizeBytes = file.sizeBytes,
            chunkSize = file.chunkSize,
            chunkFileIds = extractedIds
        )
    }

    private fun listAllFiles(prefix: String, dirInfo: DirInfo): List<Pair<String, FileInfo>> {
        return dirInfo.childNodes.flatMap { childInfo ->
            when (childInfo) {
                is FileInfo -> listOf("$prefix${dirInfo.name}/${childInfo.name}" to childInfo)
                is DirInfo -> listAllFiles(prefix = "$prefix${dirInfo.name}/", childInfo)
            }
        }
    }

    // Returns a map of file path to file info
    fun listAllFiles(): Map<String, FileInfo> {
        return listAllFiles("/", index.root).toMap()
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

    fun getInfo(path: String) = getInfo(index.root, path.trimStart('/'))

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
        if (path == "/" && nodeInfo == null) {
            throw IllegalArgumentException("setInfo call tried to remove root.")
        }
        val updatedRoot = setInfo(index.root, path.trimStart('/'), nodeInfo) as DirInfo
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
