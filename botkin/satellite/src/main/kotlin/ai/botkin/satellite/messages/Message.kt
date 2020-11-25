package botkin.ai.messages

  abstract class Message {
    abstract var workerType: String
    abstract var taskId: String }

data class Schedule(
    override var workerType: String = "",
    override var taskId: String = ""
    ): Message()

data class ScheduleJob(
    override var workerType: String = "",
    override var taskId: String = "",
    val studyUID:String,
    val target: String

): Message()

data class Ok(
    override var workerType: String = "",
    override var taskId: String = ""
): Message()

data class Rejected(
    override var workerType: String = "",
    override var taskId: String = "",
    val reason:String
): Message()

data class Done(
    override var workerType: String = "",
    override var taskId: String = "",
    var data: Any?
): Message()


data class Failed(
    override var workerType: String = "",
    override var taskId: String = "",
    val reason:String
): Message()


data class Processing(
    override var workerType: String = "",
    override var taskId: String = "",
    val reason:String
): Message()