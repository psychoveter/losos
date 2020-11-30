package io.losos.common


interface AgentDescriptor {}

interface AgentSelector<T: AgentDescriptor> {
    fun match(desc: T): Boolean
}

//==Simple=string=match=descriptor======================================================================================

data class StringADescriptor(val key: String): AgentDescriptor

class StringMatchSelector(val key: String): AgentSelector<StringADescriptor> {
    override fun match(desc: StringADescriptor): Boolean = key.equals(desc.key)

}

//==Tags=match=descriptor===============================================================================================
//TODO