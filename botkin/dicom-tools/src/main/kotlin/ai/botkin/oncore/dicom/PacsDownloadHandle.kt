package ai.botkin.oncore.dicom

import ai.botkin.oncore.dicom.pipeline.dto.ImageDto
import ai.botkin.oncore.dicom.pipeline.dto.TransferDto
import ai.botkin.oncore.dicom.service.dto.GetSCURequest
import java.util.concurrent.CompletableFuture



interface PacsDownloadHandle<RES> {

    /**
     * Starts execution of the download process
     */
    fun start(): PacsDownloadHandle<ImageDto>

    /**
     * Sets callback that should be invoke on each image receive.
     * Applicable only for C-GET operation
     */
    fun onEntry(block: (RES) -> Unit): PacsDownloadHandle<ImageDto>


    /**
     * Future you may join on
     * Returns list of received RES items if accumulate was set on or empty list if was not
     * Fails exceptionally as usual CompletableFuture if something happened during the processing
     */
    val future: CompletableFuture<List<RES>>

    /**
     * Marks handle to accumulate results returned for onEntry method.
     * It's useful for sync approach but dangerous due overflow
     */
    fun accumulate(): PacsDownloadHandle<ImageDto>

    /**
     * Clears all related resources such as temporary files, etc.
     */
    fun clear()
}
