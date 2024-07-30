package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigManager
import gecko10000.telefuse.model.FileList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IndexManager : KoinComponent {

    private val json: Json by inject()
    private val configFile: JsonConfigManager<Config> by inject()
    private val botManager: BotManager by inject()

    private val config: Config
        get() = configFile.value

    lateinit var index: FileList

    private var existingIndexChunks = listOf<String>()

    suspend fun loadIndex() = coroutineScope {
        val indexChunks = config.indexFiles
            .map {
                async {
                    botManager.downloadBytes(it)
                }
            }
            .awaitAll()
            .map { it.toString(Charsets.UTF_8) }
        val jsonString = indexChunks.joinToString(separator = "")
        existingIndexChunks = indexChunks
        index = json.decodeFromString(jsonString)
    }

    private suspend fun updateIndexChunk(index: Int, newChunk: String): FileId? {
        val oldChunk = existingIndexChunks.getOrNull(index)
        if (oldChunk == newChunk && config.indexFiles.getOrNull(index) != null) return null
        return botManager.uploadString("~index-$index", newChunk)
    }

    suspend fun saveIndex(chunkSize: Int = Constant.MAX_CHUNK_SIZE) = coroutineScope {
        val jsonString = json.encodeToString(index)
        val newIndexChunks = jsonString.windowed(size = chunkSize, step = chunkSize, partialWindows = true)
        val newIndexFiles = newIndexChunks.mapIndexed { i, newChunk ->
            async {
                updateIndexChunk(i, newChunk) ?: config.indexFiles[i]
            }
        }.awaitAll()
        configFile.value = config.copy(indexFiles = newIndexFiles)
        configFile.save()
    }

}
