import ai.botkin.satellite.CTFilter

fun main() {
//    val folders = Files.list(Paths.get("/Users/valentin/programming/c++"))
//        .filter{ Files.isDirectory(it)}.toList()
//    val numberOfFiles = folders.map { it.toString() to Files.list(Paths.get(it.toString())).count()}
//    print(numberOfFiles)
//    val dicom = DicomUtils.loadDicomObject(
//        File("/Users/valentin/programming/with_pathology/seria/1.2.276.0.7230010.3.1.4.8323329.15480.1517874386.97250"))
//    if (dicom != null) {
//        print(dicom)
//    }
    val seria = CTFilter().filterSeries("1.2.392.200036.9116.2.5.1.11341.1409398444.1586329368.638766")
    print(seria)
}
