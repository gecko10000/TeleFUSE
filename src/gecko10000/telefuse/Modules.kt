package gecko10000.telefuse

import gecko10000.telefuse.cache.BatchingUpdateCache
import gecko10000.telefuse.cache.IChunkCache
import gecko10000.telefuse.config.Config
import gecko10000.telefuse.config.JsonConfigWrapper
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.io.File

fun tgModules() = module {
    single<Json> {
        Json {
            //prettyPrint = true
            encodeDefaults = true
        }
    }
    single<JsonConfigWrapper<Config>>(createdAtStart = true) {
        JsonConfigWrapper(
            configDirectory = File("."),
            configName = "config.json",
            initialValue = Config(),
            serializer = Config.serializer(),
        )
    }
    single(createdAtStart = true) { RemoteIndexManager() }
    single(createdAtStart = true) { IndexManager() }
    single(createdAtStart = true) { BotManager() }
    single(createdAtStart = true) { ReadWriteHelper() }
    single<IChunkCache>(createdAtStart = true) { BatchingUpdateCache() }
}
