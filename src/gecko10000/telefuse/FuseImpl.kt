package gecko10000.telefuse

import ru.serce.jnrfuse.FuseStubFS
import java.nio.file.Path

class FuseImpl(val mountPoint: Path) : FuseStubFS() {
    
}
