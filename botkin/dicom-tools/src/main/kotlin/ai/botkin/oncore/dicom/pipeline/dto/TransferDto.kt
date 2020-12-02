package ai.botkin.oncore.dicom.pipeline.dto

import ai.botkin.oncore.dicom.pipeline.TransferState

data class TransferDto<T> (
    val status: TransferState,
    val payload: T? = null
)