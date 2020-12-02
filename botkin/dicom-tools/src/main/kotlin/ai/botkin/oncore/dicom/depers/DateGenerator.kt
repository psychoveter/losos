package ai.botkin.oncore.dicom.depers

import java.text.SimpleDateFormat
import java.util.*

class DateGenerator {
    private val format = SimpleDateFormat("yyyyymmdd")

    fun randomDate(): String {
        val gc = GregorianCalendar()
        val year = randBetween(1900, 2010)
        gc[Calendar.YEAR] = year
        val dayOfYear = randBetween(1, gc.getActualMaximum(Calendar.DAY_OF_YEAR))
        gc[Calendar.DAY_OF_YEAR] = dayOfYear
        val date = gc.time
        return format.format(date)
    }

    private fun randBetween(start: Int, end: Int): Int {
        return start + Math.round(Math.random() * (end - start)).toInt()
    }
}
