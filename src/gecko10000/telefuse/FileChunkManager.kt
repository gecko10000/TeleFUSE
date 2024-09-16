package gecko10000.telefuse

import gecko10000.telefuse.cache.IChunkCache
import gecko10000.telefuse.model.info.FileInfo
import jnr.ffi.Pointer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.serce.jnrfuse.ErrorCodes
import java.util.logging.Logger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class FileChunkManager : KoinComponent {

    val log = Logger.getLogger(this::class.qualifiedName)

    private val chunkCache: IChunkCache by inject()

    fun write(filePath: String, buf: Pointer, size: Long, offset: Long): Int {
        val fileInfo = chunkCache.shardedIndex.getInfo(filePath) as? FileInfo
        fileInfo ?: return -ErrorCodes.ENOENT()
        val endOfWriteRange = offset + size
        val newFileSize = max(endOfWriteRange, fileInfo.sizeBytes)
        val writeRange = LongRange(offset, endOfWriteRange - 1)
        val chunks = calculateChunkIndices(fileInfo, size, offset).map { FileChunk(fileInfo, it) }
        var bufferOffset = 0
        for (chunk in chunks) {
            // If it needs a partial update, we
            // shall have to retrieve the chunk first.
            val needsPartialUpdate: Boolean = chunk.bytesCovered.first < writeRange.first
                    || chunk.bytesCovered.last > writeRange.last
            val chunkBytes = if (needsPartialUpdate && chunk.chunkIndex < chunk.fileInfo.chunkFileIds.size) {
                chunkCache.getChunk(chunk.fileInfo.chunkFileIds[chunk.chunkIndex])
            } else {
                ByteArray(fileInfo.chunkSize)
            }
            val toWrite = min(size - bufferOffset, fileInfo.chunkSize.toLong()).toInt()
            val startIndex = (
                    if (needsPartialUpdate)
                        (offset + bufferOffset) % fileInfo.chunkSize
                    else 0
                    ).toInt()
            buf.get(bufferOffset.toLong(), chunkBytes, startIndex, toWrite)
            bufferOffset += toWrite
            log.info("${chunk.bytesCovered.last + 1} >= $newFileSize")
            val usedBytes = if (chunk.bytesCovered.last + 1 >= newFileSize) {
                val lastChunkSize = (newFileSize % fileInfo.chunkSize).toInt()
                chunkBytes.copyOfRange(0, lastChunkSize)
            } else {
                chunkBytes
            }
            chunkCache.putChunk(filePath, chunk.chunkIndex, usedBytes, newFileSize)
        }
        return bufferOffset
    }

    fun read(filePath: String, buf: Pointer, size: Long, offset: Long): Int {
        val fileInfo = chunkCache.shardedIndex.getInfo(filePath) as? FileInfo
        fileInfo ?: return -ErrorCodes.ENOENT()
        val chunks = calculateChunkIndices(fileInfo, size, offset).map { FileChunk(fileInfo, it) }
        var bufferOffset = 0
        for (chunk in chunks) {
            val chunkBytes = chunkCache.getChunk(chunk.fileInfo.chunkFileIds[chunk.chunkIndex])
            println(chunkBytes.size)
            val startIndex = ((offset + bufferOffset) % fileInfo.chunkSize).toInt()
            val toRead = min(size - bufferOffset, chunkBytes.size.toLong()).toInt()
            buf.put(bufferOffset.toLong(), chunkBytes, startIndex, toRead)
            bufferOffset += toRead
        }
        return bufferOffset
    }

    fun truncate(filePath: String, newSize: Long): Int {
        val fileInfo = chunkCache.shardedIndex.getInfo(filePath)
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

    // This will update the contents of the
    // file chunk, and mark it as dirty (to
    // be uploaded to Telegram).
    private fun updateChunk(filePath: String, fileChunk: FileChunk, newContent: ByteArray, newSize: Long) {
        chunkCache.putChunk(filePath, fileChunk.chunkIndex, newContent, newSize)
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

    data class FileChunk(val fileInfo: FileInfo, val chunkIndex: Int) {
        val bytesCovered: LongRange by lazy {
            val startByte = fileInfo.chunkSize.toLong() * chunkIndex
            LongRange(startByte, startByte - 1 + fileInfo.chunkSize)
        }
    }

}
