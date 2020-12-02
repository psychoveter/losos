package ai.botkin.oncore.dicom.service

import org.dcm4che3.data.Attributes
import org.dcm4che3.data.ElementDictionary
import org.dcm4che3.data.Tag
import org.dcm4che3.data.VR
import org.dcm4che3.media.RecordType
import org.dcm4che3.util.StringUtils
import org.dcm4che3.util.TagUtils

interface BotkinDicomService {

    //--Utilities-methods-for-service-implementations-------------------------------------------------------------------

    fun setLevel(keys: Attributes, recordType: RecordType) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, recordType.toString())
    }

    fun addKey(keys: Attributes, tag: Int, value: String?): String {
        if (value == null) {
            return ""
        }
        val isPrivate = TagUtils.isPrivateTag(tag)
        val tagType = if (isPrivate) VR.LO else ElementDictionary.vrOf(tag, null)
        val tagString = tag.toString(16)
//        logger.info("Setting tag $tagString with type $tagType to value $value with private $isPrivate")
        keys.setString(tag, tagType, *StringUtils.split(value, '/'))
        return "[$tagString:$value]"
    }


    fun addSimpleKey(keys: Attributes, tag: Int, value: String?): String {
        if (value == null) {
            return ""
        }
        val vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag))
        keys.setString(tag, vr, value)
        return "[ ${tag.toString(16)} :  $value ]"
    }

}