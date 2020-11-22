package io.losos.process.agent

import io.losos.common.AgentDescriptor
import io.losos.common.AgentSelector
import io.losos.common.StringADescriptor
import io.losos.platform.LososPlatform

data class AgentDef<T: AgentDescriptor>(
        val name: String,
        val descriptor: T
)

interface AgentRegistry<T: AgentDescriptor> {

    suspend fun getAgents(): List<AgentDef<T>>

    suspend fun getAgents(selector: AgentSelector<T>): List<AgentDef<T>> {
        return getAgents().filter { selector.match(it.descriptor) }
    }

}


//==etcd=StringADescriptor=agent=registry===============================================================================
/**
 * Simple agent registry which performs all operations by direct queries to event bus
 */
class EtcdAgentManagerV1(val eventBus: LososPlatform): AgentRegistry<StringADescriptor> {

    override suspend fun getAgents(): List<AgentDef<StringADescriptor>> {
        return eventBus
                .readPrefix("${LososPlatform.PREFIX_AGENTS}/")
                .entries
                .map { AgentDef(it.key, StringADescriptor.fromJson(it.value)) }
                .toList()
    }

}


/**
 * Reactive agent registry which reflects state of the registry in reactive manner
 */
class EtcdAgentManagerV2(val eventBus: LososPlatform): AgentRegistry<StringADescriptor> {
    override suspend fun getAgents(): List<AgentDef<StringADescriptor>> {
        TODO("Not yet implemented")
    }
}