package ai.botkin.oncore.dicom.depers

import ai.botkin.oncore.dicom.util.DicomUtil
import org.dcm4che3.data.Attributes

/**
 * Depersonalization service is used for masking privacy sensitive data.
 * Following strategies should  be supported:
 * - Random: replace tag with a random value corresponding to a type of the tag value
 * - Delete: remove tag value
 * - Consistent: replace the same values with the same encoding values
 * - ConsistentPersistent: like Consistent, but save map of depersonalization values to allow inversion of
 *   depersonalization operation at the client side.
 *
 * Right now, simple random strategy is implemented.
 */
class DeperService(
    val tags: IntArray
) {

    fun depersonalize(noBulkData: Attributes) {
        for(tag in tags) {
            DicomUtil.randomizeOrDelete(noBulkData, tag)
        }
    }

}