package gecko10000.telefuse

import dev.inmo.tgbotapi.requests.abstracts.FileId
import gecko10000.telefuse.model.FullFileInfo
import gecko10000.telefuse.model.remote.RemoteFS
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
        val filesystem = RemoteFS(
            files = mutableMapOf(
                "test" to FullFileInfo(
                    sizeBytes = 12,
                    permissions = 644,
                    uid = 0,
                    gid = 0,
                    chunkSize = Constant.MAX_CHUNK_SIZE,
                    fileChunks = listOf(
                        FileId("file")
                    )
                )
            )
        )
        indexManager.index = filesystem
        runBlocking {
            indexManager.saveIndex(chunkSize = Constant.MAX_CHUNK_SIZE)
            indexManager.loadIndex()
        }
        assert(indexManager.index == filesystem)
    }

}
