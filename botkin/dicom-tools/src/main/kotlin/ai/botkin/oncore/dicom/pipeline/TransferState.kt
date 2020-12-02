package ai.botkin.oncore.dicom.pipeline


/**
 * Transfer state holds information about retrieval process from PACS server.
 *
 * <b>Started</b> is sent when get-rq was sent to the PACS
 *
 * <b>Receiving</b> indicates actual image transfer:
 *  - total - is total number of expected images
 *  - completed - previously sent images
 *  - remaining - number of images to store including this one
 *  - warning - number of images received with PendingWarning status
 *  - failed - number of failed images
 *
 * <b>Completed</b> last message, everything was transferred
 *
 * <b>Failed</b> last message, something happened
 *
 */
sealed class TransferState(
    val name: String,
    val time: Long
) {

    class Started(): TransferState("started", System.currentTimeMillis())

    data class Receiving(
        val dicomStatus: Int,
        val total: Int? = null,
        val completed: Int? = null,
        val remaining: Int? = null,
        val warning: Int? = null,
        val failed: Int? = null
    ): TransferState("receiving", System.currentTimeMillis())

    data class Completed(
        val dicomStatus: Int,
        val total: Int? = null,
        val completed: Int? = null,
        val remaining: Int? = null,
        val warning: Int? = null,
        val failed: Int? = null
    ): TransferState("completed", System.currentTimeMillis())

    data class Failed(
        val dicomStatus: Int,
        val total: Int? = null,
        val completed: Int? = null,
        val remaining: Int? = null,
        val warning: Int? = null,
        val failed: Int? = null,
        val reason: String? = null,
        val exception: Throwable? = null
    ): TransferState("failed", System.currentTimeMillis())

}