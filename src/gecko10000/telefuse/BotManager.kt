package gecko10000.telefuse

import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.bot.settings.limiters.CommonLimiter
import dev.inmo.tgbotapi.extensions.api.files.downloadFile
import dev.inmo.tgbotapi.extensions.api.send.media.sendDocument
import dev.inmo.tgbotapi.requests.abstracts.FileId
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigWrapper
import io.ktor.client.plugins.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BotManager : KoinComponent {

    private val configFile: JsonConfigWrapper<Config> by inject()

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

    suspend fun uploadBytes(name: String, bytes: ByteArray): FileId =
        bot.sendDocument(config.channelId, bytes.asMultipartFile(name))
            .content.media.fileId

    suspend fun uploadString(name: String, content: String) = uploadBytes(name, content.toByteArray())

    suspend fun downloadBytes(id: FileId) = bot.downloadFile(id)

}
