package gecko10000.telefuse.model

import kotlinx.serialization.Serializable
import ru.serce.jnrfuse.struct.Timespec

@Serializable
data class Time(val seconds: Long, val nanoseconds: Int) {
    companion object {
        fun now(): Time {
            val time = System.currentTimeMillis()
            return Time(seconds = time / 1000, nanoseconds = (time % 1000).times(1_000_000).toInt())
        }

        fun fromTimespec(timespec: Timespec): Time {
            return Time(timespec.tv_sec.get(), timespec.tv_nsec.intValue())
        }
    }
}
