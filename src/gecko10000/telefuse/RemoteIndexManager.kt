package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigWrapper
import gecko10000.telefuse.model.remote.RemoteFS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

class RemoteIndexManager : KoinComponent {

    private val log = Logger.getLogger(this::class.qualifiedName)

    private val json: Json by inject()
    private val configFile: JsonConfigWrapper<Config> by inject()
    private val botManager: BotManager by inject()
    private val config: Config
        get() = configFile.value

    var index: RemoteFS = runBlocking { loadIndex() }

    // This cache is used to minimize
    // re-uploads of the index file.
    // We use lateinit because otherwise
    // it would be initialized after
    // the call to loadIndex() above.
    private lateinit var cachedRemoteChunks: List<String>

    // This retrieves and decodes the index
    // using the file IDs in the config
    private suspend fun loadIndex(): RemoteFS = coroutineScope {
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
        val decoded = if (jsonString.isEmpty()) RemoteFS() else json.decodeFromString(jsonString)
        return@coroutineScope decoded
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

    // Chunks and uploads the index to Telegram.
    // Deduplicates existing chunks.
    suspend fun saveIndex() = coroutineScope {
        val jsonString = json.encodeToString(index)
        // might need fixing if chunkSize goes over 2G
        val newIndexChunks = jsonString.windowed(
            size = Constant.MAX_CHUNK_SIZE,
            step = Constant.MAX_CHUNK_SIZE,
            partialWindows = true
        )
        val newIndexFiles = newIndexChunks.mapIndexed { i, newChunk ->
            async {
                updateIndexChunk(i, newChunk)
            }
        }.awaitAll()
        configFile.value = config.copy(indexFiles = newIndexFiles)
        configFile.save()
    }

}
