package gecko10000.telefuse.model.memory

import dev.inmo.tgbotapi.requests.abstracts.FileId

// Class representing a piece
// of a chunked file
data class FileChunk(
    val fileId: FileId? = null,
    val bytes: ByteArray? = null,
    val isDirty: Boolean = false,
) {

    // Possible states:
    // file ID               (uncached)
    // file ID, bytes, clean (cached, synced)
    // file ID, bytes, dirty (modified, not synced)
    //          bytes, dirty (created, not synced)
    // Can't have clean bytes without file ID
    private fun validateChunk() {
        if (fileId != null) return
        if (bytes != null && isDirty) return
        throw IllegalArgumentException(
            "File chunk must have file ID, dirty bytes, or file ID and bytes: $this"
        )
    }

    init {
        validateChunk()
    }

    // Auto-generated for byte array comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileChunk

        if (fileId != other.fileId) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false
        if (isDirty != other.isDirty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileId?.hashCode() ?: 0
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + isDirty.hashCode()
        return result
    }
}
