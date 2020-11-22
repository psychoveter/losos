package io.losos.common

import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.Framework


interface AgentDescriptor {
    fun toJson(): ObjectNode
}

interface AgentSelector<T: AgentDescriptor> {
    fun match(desc: T): Boolean
}

//==Simple=string=match=descriptor======================================================================================

class StringADescriptor(val key: String): AgentDescriptor {

    companion object {
        fun fromJson(json: ObjectNode): StringADescriptor = Framework
                .json2object(json, StringADescriptor::class.java)
    }

    override fun toJson(): ObjectNode = Framework.jsonMapper
                    .createObjectNode()
                    .put("key", key)

}

class StringMatchSelector(val key: String): AgentSelector<StringADescriptor> {
    override fun match(desc: StringADescriptor): Boolean = key.equals(desc.key)

}

//==Tags=match=descriptor===============================================================================================
//TODO