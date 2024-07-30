package gecko10000.telefuse.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.nio.file.Files

class JsonConfigManager<T : Any>(
    private val configFile: File,
    private val backupDirectory: File,
    json: Json? = null,
    private val initialValue: T,
    private val serializer: KSerializer<T>,
) : KoinComponent {

    companion object {
        private const val backupAmount = 5
        private const val backupStart = 0
    }

    constructor(
        configDirectory: File,
        configName: String = "config.json",
        json: Json? = null,
        initialValue: T,
        serializer: KSerializer<T>,
    ) : this(
        configDirectory.resolve(configName),
        configDirectory.resolve("backups"),
        json,
        initialValue,
        serializer
    )

    private val fileName = configFile.name
    private val defaultJson: Json by inject()
    private val stringFormat = json ?: defaultJson
    var value: T = initialValue

    init {
        reload()
    }

    private fun createIfNotExists() {
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            value = initialValue
            saveInternal()
        }
    }

    private fun saveInternal() {
        // Do this separately so any encoding
        // errors don't clear the output file.
        // Not ideal -- config is loaded into memory
        // (though it's already in memory in the first place I guess)
        val contents = stringFormat.encodeToString(serializer, value)
        configFile.writeText("$contents\n") // lol
    }

    fun save() {
        backup()
        saveInternal()
    }

    private fun loadInternalUncaught() {
        val string = configFile.readText()
        value = stringFormat.decodeFromString(serializer, string)
    }

    private fun loadInternal() {
        try {
            loadInternalUncaught()
        } catch (e: Exception) { // TODO: figure out more specific exception
            e.printStackTrace()
            saveInternal()
            loadInternalUncaught() // no chance of infinite recursion
        }
        saveInternal()
    }

    private fun load() {
        backup()
        loadInternal()
    }

    fun reload() {
        createIfNotExists()
        loadInternal()
    }


    private val backupScheme = "$fileName.%d.bak"

    private fun resolveBackupFile(index: Int) = backupDirectory.resolve(backupScheme.format(index))

    private fun shiftBackupDown(index: Int) {
        val sourceFile = resolveBackupFile(index)
        val targetFile = resolveBackupFile(index + 1)
        sourceFile.renameTo(targetFile)
    }

    private fun backup() {
        // Don't back up duplicates.
        val firstBackup = resolveBackupFile(backupStart)
        if (firstBackup.exists() && Files.mismatch(firstBackup.toPath(), configFile.toPath()) == -1L) {
            return
        }
        backupDirectory.mkdirs()
        // 4 -> 5, 3 -> 4, etc.
        for (i in backupAmount - 1 downTo backupStart) {
            shiftBackupDown(i)
        }
        configFile.copyTo(resolveBackupFile(backupStart), overwrite = true)
    }

}
