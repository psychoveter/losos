package io.losos.common

import io.losos.Framework
import io.losos.JsonObj


interface AgentDescriptor {
    fun toJson(): JsonObj
}

interface AgentSelector<T: AgentDescriptor> {
    fun match(desc: T): Boolean
}

//==Simple=string=match=descriptor======================================================================================

class StringADescriptor(val key: String): AgentDescriptor {

    companion object {
        fun fromJson(json: JsonObj): StringADescriptor = Framework
                .json2object(json, StringADescriptor::class.java)
    }

    override fun toJson(): JsonObj = Framework.jsonMapper
                    .createObjectNode()
                    .put("key", key)

}

class StringMatchSelector(val key: String): AgentSelector<StringADescriptor> {
    override fun match(desc: StringADescriptor): Boolean = key.equals(desc.key)

}

//==Tags=match=descriptor===============================================================================================
//TODO