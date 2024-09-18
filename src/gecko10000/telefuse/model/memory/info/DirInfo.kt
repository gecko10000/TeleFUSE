package gecko10000.telefuse.model.memory.info

import gecko10000.telefuse.model.Time
import ru.serce.jnrfuse.struct.FileStat
import ru.serce.jnrfuse.struct.FuseContext

data class DirInfo(
    override val name: String,
    override val permissions: Int,
    override val uid: Long,
    override val gid: Long,
    override val accessTime: Time,
    override val modificationTime: Time,
    val childNodes: Set<NodeInfo>
) : NodeInfo() {
    companion object {
        fun default(
            name: String,
            permissions: Int,
            uid: Long,
            gid: Long,
        ) = DirInfo(
            name = name,
            permissions = permissions or FileStat.S_IFDIR,
            uid = uid,
            gid = gid,
            accessTime = Time.now(),
            modificationTime = Time.now(),
            childNodes = emptySet()
        )

        fun default(
            name: String,
            permissions: Int,
            context: FuseContext,
        ) = default(name, permissions, context.uid.get(), context.gid.get())
    }
}
