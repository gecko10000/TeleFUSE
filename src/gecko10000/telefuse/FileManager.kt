package gecko10000.telefuse

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FileManager : KoinComponent {

    private val botManager: BotManager by inject()

    init {
    }

}
