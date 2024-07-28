package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import dev.inmo.tgbotapi.types.MessageId
import gecko10000.telefuse.BotManager.Companion.MAX_CHUNK_SIZE
import gecko10000.telefuse.model.FileList
import gecko10000.telefuse.model.FileMessage
import gecko10000.telefuse.model.FullFileInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FileManager : KoinComponent {

    private val botManager: BotManager by inject()

    private var fileList: FileList

    init {
        // TODO: write proper tests
        // This just tests that the index files are properly sharded
        runBlocking {
            botManager.writeIndexJson(
                Json.encodeToString(
                    FileList(
                        files = mutableMapOf(
                            "test" to FullFileInfo(
                                sizeBytes = 10,
                                permissions = 644,
                                fileMessages = listOf(
                                    FileMessage(
                                        messageId = MessageId(12345),
                                        fileId = FileId("abcde".repeat(MAX_CHUNK_SIZE))
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val indexJsonString = botManager.readIndexJson()
            fileList = Json.decodeFromString(indexJsonString)
            println(fileList)
        }
    }

}
