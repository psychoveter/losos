package ai.botkin.satellite.task


class Request<T>(val workerType: String, val tasks: List<T>){
}
interface WorkerTask
interface WorkerRequest
class MLTask(val id: String, val dicomPath:String, val markupPath: String, val target:String): WorkerTask
class ReportTask(val id:String, val dicomPath: String, val markupPath:String, val target:String, val savePaths: SavePaths):
    WorkerTask
class DownloadTask(val id: String, val studyUID: String, val downloadPath:String): WorkerTask
class UploadTask(val id: String, val uploadFiles: UploadFiles, val destination:String): WorkerTask
class UploadFiles(val srPath:String, val scPath:String, val prPath: String, val markupPath: String)
class SavePaths(val srPath:String, val prPath: String, val scPath:String)
class JobRequest(val studyUID: String, val target: String)

