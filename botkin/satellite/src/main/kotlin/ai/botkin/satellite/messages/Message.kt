package ai.botkin.satellite.messages

  abstract class TEPMessage {
    abstract var workerType: String
    abstract var taskId: String }

data class Schedule(
    override var workerType: String = "",
    override var taskId: String = ""
    ): TEPMessage()

data class ScheduleJob(
    override var workerType: String = "",
    override var taskId: String = "",
    val studyUID:String,
    val target: String

): TEPMessage()

data class Ok(
    override var workerType: String = "",
    override var taskId: String = ""
): TEPMessage()

data class Rejected(
    override var workerType: String = "",
    override var taskId: String = "",
    val reason:String
): TEPMessage()

data class Done(
    override var workerType: String = "",
    override var taskId: String = "",
    var data: Any?
): TEPMessage()


data class Failed(
    override var workerType: String = "",
    override var taskId: String = "",
    val reason:String
): TEPMessage()


data class Processing(
    override var workerType: String = "",
    override var taskId: String = "",
    val reason:String
): TEPMessage()