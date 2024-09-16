package gecko10000.telefuse

import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import kotlin.io.path.Path

class App(mountPoint: String) : KoinComponent {

    init {
        startKoin {
            modules(tgModules())
        }
        val path = Path(mountPoint)
        val fuse = FuseImpl()
        try {
            fuse.mount(path, true, true)
        } finally {
            fuse.umount()
        }
    }
}
