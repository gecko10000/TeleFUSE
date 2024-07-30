package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.model.FileList
import gecko10000.telefuse.model.FullFileInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.test.KoinTest
import org.koin.test.inject

class BotTest : KoinTest {

    private val indexManager: IndexManager by inject()

    @Test
    fun ensureIndexShardingWorks() {
        startKoin { modules(tgModules()) }
        val fileList = FileList(
            files = mutableMapOf(
                "test" to FullFileInfo(
                    sizeBytes = 12, permissions = 644, fileChunks = listOf(
                        FileId("file")
                    )
                )
            )
        )
        indexManager.index = fileList
        runBlocking {
            indexManager.saveIndex(chunkSize = 10)
            indexManager.loadIndex()
        }
        assert(indexManager.index == fileList)
    }

}
