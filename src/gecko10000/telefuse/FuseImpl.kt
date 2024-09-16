package gecko10000.telefuse

import gecko10000.telefuse.cache.IChunkCache
import gecko10000.telefuse.model.Time
import gecko10000.telefuse.model.info.DirInfo
import gecko10000.telefuse.model.info.FileInfo
import jnr.ffi.Pointer
import jnr.ffi.types.mode_t
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.ErrorCodes
import ru.serce.jnrfuse.FuseFillDir
import ru.serce.jnrfuse.FuseStubFS
import ru.serce.jnrfuse.struct.FileStat
import ru.serce.jnrfuse.struct.FuseFileInfo
import ru.serce.jnrfuse.struct.Timespec
import java.util.logging.Logger

class FuseImpl : FuseStubFS(), KoinComponent {

    private val log = Logger.getLogger(this::class.qualifiedName)

    //private val json: Json by inject()
    private val chunkCache: IChunkCache by inject()
    private val fileChunkManager: FileChunkManager by inject()

    override fun create(path: String, @mode_t mode: Long, fi: FuseFileInfo): Int {
        val fileInfo = chunkCache.shardedIndex.getInfo(path)
        if (fileInfo != null) return -ErrorCodes.EEXIST()
        chunkCache.upsertFile(path, mode.toInt(), context)
        return 0
    }

    override fun getattr(path: String, stat: FileStat): Int {
        try {
            val nodeInfo = chunkCache.shardedIndex.getInfo(path)
            nodeInfo ?: return -ErrorCodes.ENOENT()
            stat.st_mode.set(nodeInfo.permissions)
            if (nodeInfo is FileInfo) {
                stat.st_size.set(nodeInfo.sizeBytes)
            }
            stat.st_uid.set(nodeInfo.uid)
            stat.st_gid.set(nodeInfo.gid)
            return 0
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        }
    }

    override fun mkdir(path: String, mode: Long): Int {
        val nodeInfo = chunkCache.shardedIndex.getInfo(path)
        nodeInfo ?: return -ErrorCodes.EEXIST()
        chunkCache.upsertDir(path, mode.toInt(), context)
        return 0
    }

    override fun read(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int {
        return runBlocking { fileChunkManager.read(path, buf, size, offset) }
    }

    override fun readdir(path: String, buf: Pointer, filler: FuseFillDir, offset: Long, fi: FuseFileInfo): Int {
        val nodeInfo = chunkCache.shardedIndex.getInfo(path)
        nodeInfo ?: return -ErrorCodes.ENOENT()
        if (nodeInfo !is DirInfo) return -ErrorCodes.ENOTDIR()
        filler.apply(buf, ".", null, 0)
        filler.apply(buf, "..", null, 0)
        for (child in nodeInfo.childNodes) {
            filler.apply(buf, child.name, null, 0)
        }
        return 0
    }

    // Not implementing statfs because Winblows is for losers

    override fun rename(oldpath: String, newpath: String): Int {
        val nodeInfo = chunkCache.shardedIndex.getInfo(oldpath)
        nodeInfo ?: return -ErrorCodes.ENOENT()
        val newParent = chunkCache.shardedIndex.getInfo(newpath.substringBeforeLast('/'))
        newParent ?: return -ErrorCodes.ENOENT()
        if (newParent !is DirInfo) return -ErrorCodes.ENOTDIR()
        return chunkCache.renameNode(oldpath, newpath)
    }

    override fun rmdir(path: String): Int {
        val nodeInfo = chunkCache.shardedIndex.getInfo(path)
        nodeInfo ?: return -ErrorCodes.ENOENT()
        if (nodeInfo !is DirInfo) return -ErrorCodes.ENOTDIR()
        chunkCache.deleteDir(path)
        return 0
    }

    override fun truncate(path: String, size: Long): Int {
        return runBlocking { fileChunkManager.truncate(path, size) }
    }

    override fun unlink(path: String): Int {
        val nodeInfo = chunkCache.shardedIndex.getInfo(path)
        nodeInfo ?: return -ErrorCodes.ENOENT()
        chunkCache.deleteFile(path)
        return 0
    }

    override fun open(path: String?, fi: FuseFileInfo?): Int {
        return 0
    }

    override fun write(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int {
        try {
            return runBlocking { fileChunkManager.write(path, buf, size, offset) }
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        }
    }

    override fun utimens(path: String, timespec: Array<out Timespec>): Int {
        val fileInfo = chunkCache.shardedIndex.getInfo(path)
        fileInfo ?: return -ErrorCodes.ENOENT()
        val accessTime = Time.fromTimespec(timespec[0])
        val modificationTime = Time.fromTimespec(timespec[1])
        val newFileInfo = when (fileInfo) {
            is DirInfo -> fileInfo.copy(accessTime = accessTime, modificationTime = modificationTime)
            is FileInfo -> fileInfo.copy(accessTime = accessTime, modificationTime = modificationTime)
        }
        chunkCache.shardedIndex.setInfo(path, newFileInfo)
        return 0
    }

    /*override fun flush(path: String?, fi: FuseFileInfo?): Int {
        return 0
    }

    override fun fsync(path: String?, isdatasync: Int, fi: FuseFileInfo?): Int {
        return 0
    }

    override fun chown(path: String, uid: Long, gid: Long): Int {
        val fileInfo = indexManager.index.files[path]
        fileInfo ?: return -ErrorCodes.ENOENT()
        val newFileInfo = fileInfo.copy(uid = uid, gid = gid)
        indexManager.index = indexManager.index.copy(files = indexManager.index.files.plus(path to newFileInfo))
        return 0
    }

    override fun setxattr(path: String, name: String, value: Pointer, size: Long, flags: Int): Int {
        log.info("$name, ${value.getString(0, size.toInt(), Charset.defaultCharset())}")
        return 0
    }

    override fun getxattr(path: String, name: String, value: Pointer, size: Long): Int {
        log.info("$name, ${value.getString(0, size.toInt(), Charset.defaultCharset())}")
        return 0
    }*/

}
