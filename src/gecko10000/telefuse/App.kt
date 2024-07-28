package gecko10000.telefuse

import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin

class App : KoinComponent {

    init {
        startKoin {
            modules(tgModules())
        }
    }
}
