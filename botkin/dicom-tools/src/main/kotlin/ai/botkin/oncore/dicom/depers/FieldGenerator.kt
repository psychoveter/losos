package ai.botkin.oncore.dicom.depers

import java.security.SecureRandom
import java.util.*

class FieldGenerator {
    companion object {

        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ".toCharArray()

        private val random: Random = SecureRandom()

        fun randomStringForArray(value: CharArray): String? {
            for (i in value.indices) {
                value[i] = nextRandomChar()
            }
            return String(value)
        }

        fun nextDicomUID(): String? {
            val sb = StringBuilder()
            while (sb.toString().length < 52) {
                sb.append(UUID.randomUUID().toString().replace("\\D".toRegex(), ""))
            }
            val v = sb.toString()
            return String.format("%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
                v.substring(0, 1),
                v.substring(1, 2),
                v.substring(2, 3),
                v.substring(3, 4),
                v.substring(4, 5),
                v.substring(5, 6),
                v.substring(6, 11),
                v.substring(11, 12),
                v.substring(12, 13),
                v.substring(13, 14),
                v.substring(14, 18),
                v.substring(18, 22),
                v.substring(22, 52))
        }

        private fun nextRandomChar(): Char {
            return chars[random.nextInt(chars.size)]
        }
    }
}
