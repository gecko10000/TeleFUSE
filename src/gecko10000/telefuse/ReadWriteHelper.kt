package gecko10000.telefuse

import gecko10000.telefuse.cache.IChunkCache
import gecko10000.telefuse.model.memory.info.FileInfo
import jnr.ffi.Pointer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.ErrorCodes
import java.util.logging.Logger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ReadWriteHelper : KoinComponent {

    val log = Logger.getLogger(this::class.qualifiedName)

    private val chunkCache: IChunkCache by inject()

    fun write(filePath: String, buf: Pointer, size: Long, offset: Long): Int {
        log.info("Writing $size bytes to $filePath at $offset")
        val fileInfo = chunkCache.indexManager.getInfo(filePath) as? FileInfo
        fileInfo ?: return -ErrorCodes.ENOENT()
        val endOfWriteRange = offset + size
        val newFileSize = max(endOfWriteRange, fileInfo.sizeBytes)
        val chunkIndices = calculateChunkIndices(fileInfo, size, offset)
        var bufferOffset = 0
        for (chunkIndex in chunkIndices) {
            // Either we start writing `offset % fileInfo.chunkSize`
            // bytes in (on the first chunk), or we start at
            // the start of the chunk (for the other ones)
            val chunkStartByte = ((offset + bufferOffset) % fileInfo.chunkSize).toInt()
            val endByteExclusive = fileInfo.chunkSize.toLong() * (chunkIndex + 1)
            val bytesToWrite = min(size - bufferOffset, endByteExclusive - chunkStartByte).toInt()
            // Need to write however much is less,
            // amount of bytes remaining in `write` or
            // amount of bytes available in FileChunk.
            val chunkBytes = if (chunkIndex >= fileInfo.chunks.size) {
                ByteArray(fileInfo.chunkSize)
            } else {
                chunkCache.getChunk(filePath, chunkIndex)
            }
            log.info("Buffer is ${chunkBytes.size} bytes")
            log.info("Calling buf.get($bufferOffset, <bytes>, ${chunkStartByte % fileInfo.chunkSize}, $bytesToWrite)")
            buf.get(bufferOffset.toLong(), chunkBytes, chunkStartByte % fileInfo.chunkSize, bytesToWrite)
            bufferOffset += bytesToWrite
            chunkCache.putChunk(filePath, chunkIndex, chunkBytes, newFileSize)
        }
        return bufferOffset
    }

    fun read(filePath: String, buf: Pointer, size: Long, offset: Long): Int {
        val fileInfo = chunkCache.indexManager.getInfo(filePath) as? FileInfo
        fileInfo ?: return -ErrorCodes.ENOENT()
        val chunkIndices = calculateChunkIndices(fileInfo, size, offset)
        var bufferOffset = 0
        for (chunkIndex in chunkIndices) {
            val chunkBytes = chunkCache.getChunk(filePath, chunkIndex)
            val startIndex = ((offset + bufferOffset) % fileInfo.chunkSize).toInt()
            val toRead = min(size - bufferOffset, chunkBytes.size.toLong() - startIndex % fileInfo.chunkSize).toInt()
            buf.put(bufferOffset.toLong(), chunkBytes, startIndex, toRead)
            bufferOffset += toRead
        }
        return bufferOffset
    }

    fun truncate(filePath: String, newSize: Long): Int {
        val fileInfo = chunkCache.indexManager.getInfo(filePath)
        fileInfo ?: return -ErrorCodes.ENOENT()
        if (fileInfo !is FileInfo) return -ErrorCodes.EISDIR()
        // We don't need to remove extraneous bytes
        // from the last file, since they won't be
        // considered anyways.
        val remainingChunkCount = (newSize / fileInfo.chunkSize).toInt()
        chunkCache.putChunk(filePath, remainingChunkCount, null, newSize)
        /*val copiedChunkList = fileInfo.chunkFileIds.slice(IntRange(0, remainingChunkCount - 1))
        val newFileInfo = fileInfo.copy(
            sizeBytes = newSize,
            chunkFileIds = copiedChunkList
        )
        chunkCache.ch
        indexManager.index = indexManager.index.copy(
            files = indexManager.index.files.plus(filePath to newFileInfo)
        )*/
        return 0
    }

    // Gets the indices required by the operation.
    // Let's imagine a chunk size of 10.
    // If size is 10 and offset is 0, we want 0 start and 1 endExcl.
    // If size is 11 and offset is 0, we want 0 and 2.
    // If size is 10 and offset is 1, we want 0 and 2.
    // If size is 1 and offset is 9, we want 0 and 1.
    // If size is 11 and offset is 9, we want 0 and 2.
    // If size is 12 and offset is 9, we want 0 and 3.
    private fun calculateChunkIndices(fileInfo: FileInfo, size: Long, offset: Long): IntRange {
        val startIndex = offset / fileInfo.chunkSize
        val endIndexExclusive = ceil((offset + size) / fileInfo.chunkSize.toDouble()).toLong()
        return IntRange(startIndex.toInt(), (endIndexExclusive - 1).toInt())
    }

}
