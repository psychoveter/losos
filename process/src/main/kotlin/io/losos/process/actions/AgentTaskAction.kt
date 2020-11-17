package io.losos.process.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import io.losos.Framework
import io.losos.JsonObj
import io.losos.eventbus.Event
import io.losos.eventbus.EventBus
import io.losos.common.AgentTask
import io.losos.process.engine.*
import java.util.*

class AgentTaskAction(
        def: AgentTaskActionDef,
        ctx: GANContext
): AbstractAction<AgentTaskActionDef>(def, ctx) {

    private val self = this

    companion object {
        val SLOT_RESPONSE = SlotId.eventOnGuardId("response")
        val SLOT_EXCEPTION = SlotId.eventOnGuardId("exception")
        val SLOT_CTX = SlotId.varId("ctx")
    }

    override suspend fun action(input: ActionInput) {
        log("start agent task action")
        val taskCtx: AgentTaskCtx = with(input[SLOT_CTX]){ this?.payload as? AgentTaskCtx} ?: AgentTaskCtx(0, null)
        val exception: Event? = with(input[SLOT_EXCEPTION]){ this?.event }
        val inputSlots: Map<String, Slot> = input.slots.filterKeys { it != SLOT_CTX.name && it != SLOT_EXCEPTION.name }

        //first run
        if ( taskCtx.attempt == 0L ) {
            when(def.schedulePolicy.policy) {
                SchedulePolicyType.DIRECT -> {
                    //fire success guard
                    val gSuccess = guard(def.guardSuccess) {
                        slot(SLOT_RESPONSE)
                        slot(SLOT_CTX) { payload = taskCtx }
                    }
                    //fire failure_light guard
                    val gRetry = guard(def.guardRetry) {
                        slot(SLOT_EXCEPTION)
                        slot(SLOT_CTX) { payload = taskCtx }
                    }
                    //fire failure_total guard
                    val gFailure = guard(def.guardFailure) {
                        slot(SLOT_EXCEPTION)
                    }
                    scheduleOnProcess {
                        placeTask(
                                this.context,
                                self.def.schedulePolicy.agentId!!,
                                inputSlots,
                                gSuccess[SLOT_RESPONSE].eventPath(),
                                gRetry[SLOT_EXCEPTION].eventPath(),
                                gFailure[SLOT_EXCEPTION].eventPath()
                        )
                    }

                }
                SchedulePolicyType.TAGS_LEAST_LOADED -> {
                    TODO("Unsupported schedule policy")
                }
                SchedulePolicyType.CRITERION -> {
                    TODO("Unsupported schedule policy")
                }
            }
        }

        if ( taskCtx.attempt > 0L ) {
            when(def.schedulePolicy.reschedulePolicy) {
                ReschedulePolicyType.THE_SAME -> {
                    scheduleOnProcess {
                        placeTask(this.context, taskCtx.previousAgent!!, inputSlots,
                                "",
                                "",
                                "")
                    }
                }
                ReschedulePolicyType.ANOTHER -> {
                    TODO("Unsupported reschedule policy")
                }
                ReschedulePolicyType.SCHEDULE_AGAIN -> {
                    TODO("Unsupported reschedule policy")
                }
            }
        }

    }

    private fun placeTask(
            ganCtx: GANContext,
            agentId: String,
            input: Map<String, Slot>,
            successEventPath: String,
            retryEventPath: String,
            failureEventPath: String
    ) {
        val id = UUID.randomUUID().toString()
        val payload: JsonObj = Framework.jsonMapper.createObjectNode()
        log("Place task $id at agent $agentId")

        input.values.filterIsInstance<EventOnGuardSlot>()
             .forEach { payload.set<JsonObj>(it.id, it.event?.payload) }

        //TODO add taskId and retry counter to task path and id
        val task = AgentTask(
                id = UUID.randomUUID().toString(),
                type = def.taskType,
                payload = payload,
                successEventPath = successEventPath,
                retryEventPath = retryEventPath,
                failureEventPath = failureEventPath
        )
        ganCtx.eventBus().emit(EventBus.agentTaskPath(agentId, task.id), Framework.object2json(task))
    }
}

data class AgentTaskCtx(
    val attempt: Long = 0,
    val previousAgent: String? = null,
    val payload: JsonObj = Event.emptyPayload()
) {
    fun addAttempt() = AgentTaskCtx(attempt + 1, previousAgent, payload)
    fun addAttemptOnNewAgent(agentId: String) = AgentTaskCtx(attempt + 1, agentId, payload)
}


//==def=================================================================================================================

//enum class SchedulingPolicy {
//    DIRECT,
//}


@JsonTypeName("agent_task")
data class AgentTaskActionDef (
        override val id: String,
        override val runGuards: List<String>,
        val guardSuccess: String,
        val guardRetry: String,
        val guardFailure: String,
        val taskType: String,
        val retryPolicy: RetryPolicyDef,
        val schedulePolicy: SchedulePolicyDef
): ActionDef(id, runGuards)

data class RetryPolicyDef(
    val policy: RetryPolicyType,
    val maxAttempts: Int = 3
)
enum class RetryPolicyType {
    NO, MAX_ATTEMPTS
}

data class SchedulePolicyDef(
    val policy: SchedulePolicyType,
    //properties of direct policy
    val agentId: String? = null,
    //properties of tag-least-load policy
    val tags: List<String> = listOf(),
    //properties of criteria scheduler
    val reschedulePolicy: ReschedulePolicyType = ReschedulePolicyType.THE_SAME
)

enum class SchedulePolicyType {
    DIRECT, TAGS_LEAST_LOADED, CRITERION
}

enum class ReschedulePolicyType {
    THE_SAME, ANOTHER, SCHEDULE_AGAIN
}