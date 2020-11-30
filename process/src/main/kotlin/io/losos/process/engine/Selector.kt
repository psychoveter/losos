package io.losos.process.engine

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.node.ObjectNode
import io.losos.platform.Event


//==entities============================================================================================================

interface Selector {
    fun check(e: Event<*>): Boolean
}

class PrefixSelector(val prefix: String): Selector {
    override fun check(e: Event<*>): Boolean = e.fullPath.startsWith(prefix)
}


//==definitions=========================================================================================================

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes(
        JsonSubTypes.Type(value = PrefixSelectorDef::class, name = "prefix")
)
interface SelectorDef

@JsonTypeName("prefix")
data class PrefixSelectorDef (val prefix: String, val absolute: Boolean = false): SelectorDef

