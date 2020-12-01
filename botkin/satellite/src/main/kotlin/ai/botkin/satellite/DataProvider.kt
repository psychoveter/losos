package ai.botkin.satellite

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.util.SafeClose
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

typealias SeriaPath = String

class Series(val path:String)

object FilePaths{
    //TODO change to configuration properties
    val studiesBasePath = "/tmp/dicoms"
    val reportsBasePath = "/tmp/reports"
    val markupBasePath = "/tmp/markups"
}

object DataProvider{

    val paths = FilePaths
    val ctFilter = CTFilter()
    val filters = mapOf("CT" to ctFilter, "CTCR2" to ctFilter,
        "CT2" to ctFilter,
        "FCT" to ctFilter,
                            "DxSyndromeNewMarkup" to DXFilter(), "MG" to MGFilter()
    )
    fun chooseSeries(studyUID: String, target:String): SeriaPath?{
        return "${FilePaths.studiesBasePath}/$studyUID/${filters[target]!!.filterSeries(studyUID)}"
    }
//    fun getDataForML(studyUID: String, target:String):String{
//        return ""
//    }
//    fun getDataForReporter(studyUID: String):String{
//        return ""
//    }
//    fun getDataForDownloader(studyUID: String):String{
//        return studiesBasePath
//    }
//    fun getDataForUploader(studyUID: String):String{
//        return ""
//    }
}

interface SeriesFilter{
    fun filterSeries(studyUID:String): SeriaPath?
}

class DXFilter: SeriesFilter {
    override fun filterSeries(studyUID: String): SeriaPath? {
        /*
        Stub for testing.
        * */
        val folders = Files.list(Paths.get("${FilePaths.studiesBasePath}/${studyUID}"))
            .filter{Files.isDirectory(it)}.toList()
        return "${folders.firstOrNull()}"
    }

}
class CTFilter: SeriesFilter {
    //TODO add checking organ tag and filtering by convolution kernel
    data class SeriaInfo(val seriesUID:String, val size:Long, val sliceThickness:Double)

    val sliceThicknessPredicate = {seriaInfo: SeriaInfo -> seriaInfo.sliceThickness < 5}
    val sliceNumberPredicate = {it:Path -> Files.list(Paths.get(it.toString())).count() > 17}

    override fun filterSeries(studyUID: String): SeriaPath? {
        val folders = Files.list(Paths.get("${FilePaths.studiesBasePath}/${studyUID}"))
            .filter{Files.isDirectory(it)}.toList()

        val seriesFilteredByNumberOfSlices = folders.filter(sliceNumberPredicate).filterNotNull()

        if (seriesFilteredByNumberOfSlices.isEmpty()){
            return null
        }

        val seriesUids = seriesFilteredByNumberOfSlices.map { it.toString().split("/").last()}
        val seriesSize = seriesFilteredByNumberOfSlices.map { Files.list(Paths.get(it.toString())).count() }
        val seriesSliceThickness = seriesFilteredByNumberOfSlices
                    .map { Files.list(Paths.get(it.toString())).toList()[0] }
                    .map {
                        DicomUtils.loadDicomObject(File(it.toString())).getString(Tag.SliceThickness).toDouble()
                    }
        val seriesInfos = zipMultiple(seriesUids, seriesSize, seriesSliceThickness)
            .map { SeriaInfo(seriesUID = it[0] as String,
                            size = it[1] as Long, sliceThickness = it[2] as Double
                            ) }
        val seriesFilteredBySliceThickness = seriesInfos.filter(sliceThicknessPredicate)
        if (seriesFilteredBySliceThickness.isEmpty()){
            return null
        }
        return seriesFilteredBySliceThickness.sortedBy { it.sliceThickness }.first().seriesUID
    }

}

class MGFilter: SeriesFilter {
    override fun filterSeries(studyUID: String): SeriaPath {
        return ""
    }
}



object DicomUtils{
    fun loadDicomObject(f: File): Attributes {
        val dis = DicomInputStream(f)
        return try {
            dis.readDataset(-1, -1)
        } finally {
            SafeClose.close(dis)
        }
    }
}

fun zipMultiple(vararg lists: List<Any>):List<List<Any>>{
    val minLength = lists.map { it.size  }.minOrNull()?: return emptyList()
    val result = ArrayList<List<Any>>(minLength)
    val iterators = lists.map { it.iterator() }
    var i = 0
    while (i < minLength){
        result.add(iterators.map { it.next() })
        i++
        }
    return result
    }