package gecko10000.telefuse

import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.bot.settings.limiters.CommonLimiter
import dev.inmo.tgbotapi.extensions.api.files.downloadFile
import dev.inmo.tgbotapi.extensions.api.send.media.sendDocument
import dev.inmo.tgbotapi.requests.abstracts.InputFile
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigManager
import gecko10000.telefuse.model.FileMessage
import io.ktor.client.plugins.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.StringBufferInputStream
import java.nio.charset.Charset

class BotManager : KoinComponent {

    private val configFile: JsonConfigManager<Config> by inject()

    private val config: Config
        get() = configFile.value

    private val bot = telegramBot(config.token) {
        requestsLimiter = CommonLimiter()
        client = client.config {
            install(HttpTimeout) {
                requestTimeoutMillis = config.requestTimeoutMs
            }
        }
    }

    companion object {
        const val MAX_CHUNK_SIZE = 20 * 1024 * 1024
    }

    init {
        /*runBlocking {
            val res = bot.sendDocument(config.channelId, InputFile.fromInput("test") {
                StringBufferInputStream("t".repeat(MAX_CHUNK_SIZE)).asInput()
            })
            val array = bot.downloadFile(res.content.media.fileId)
            println(array.size)
        }*/
    }

    private val savedConfigChunks: MutableList<String> = mutableListOf()

    suspend fun readIndexJson(): String = coroutineScope {
        config.indexFiles
            .map {
                async {
                    bot.downloadFile(it.fileId)
                }
            }
            .awaitAll()
            .map { it.toString(Charset.defaultCharset()) }
            .also { savedConfigChunks.clear(); savedConfigChunks.addAll(it) }
            .joinToString(separator = "")
    }

    suspend fun writeIndexJson(string: String) = coroutineScope {
        val newChunks = string.windowed(size = MAX_CHUNK_SIZE, step = MAX_CHUNK_SIZE, partialWindows = true)
        val indicesThatNeedUpdating = mutableListOf<Int>()
        for (i in newChunks.indices) {
            if (i >= savedConfigChunks.size || newChunks[i] != savedConfigChunks[i]) {
                indicesThatNeedUpdating += i
            }
        }
        val updatedResponses = indicesThatNeedUpdating.map { i ->
            async {
                val inputFile = InputFile.fromInput("~index-$i") {
                    // TODO: find a way to improve this?
                    StringBufferInputStream(newChunks[i]).asInput()
                }
                // Messages CANNOT be edited with a new file.
                // File must exist in another message to be
                // edited into an existing message, so we just
                // send a new one.
                bot.sendDocument(config.channelId, inputFile)
            }
        }.awaitAll()
        val newIndexFileList = newChunks.indices.map { i ->
            if (i in indicesThatNeedUpdating) {
                FileMessage(updatedResponses[i].messageId, updatedResponses[i].content.media.fileId)
            } else {
                config.indexFiles[i]
            }
        }
        config.indexFiles.clear()
        config.indexFiles.addAll(newIndexFileList)
        configFile.save()
    }

}
