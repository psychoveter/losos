package botkin.ai

import botkin.ai.datamodel.Task
import org.dizitart.kno2.filters.eq
import org.dizitart.kno2.nitrite
import org.dizitart.no2.Nitrite
import org.dizitart.no2.mapper.JacksonMapper
import org.dizitart.no2.objects.ObjectRepository

enum class RestorePolicy{
    DELETING_PENDING_TASKS
}

val policyToRestoreAction = mapOf<RestorePolicy, (taskDao:ObjectRepository<Task>)  -> Unit> (
    RestorePolicy.DELETING_PENDING_TASKS to { taskDao ->
        val taskIds = taskDao.find(Task::status eq "processing").map { it.id }
        if (taskIds.isNotEmpty()){
            val prevTasks = taskIds.map { taskDao.find(Task::next eq it).firstOrNull()}
            for (task in prevTasks){
                if(task != null){
                    taskDao.remove(Task::id eq task.next)
                    task.next = null
                }
            }
        }
    }
)

class RepositoryStateRestorer(val taskDao:ObjectRepository<Task>, policiesToActions: RestorePolicy,
                              val policyToActions:Map<RestorePolicy,
                                          (taskDao:ObjectRepository<Task>)  -> Unit> = policyToRestoreAction
) {
    fun restore(restorePolicy: RestorePolicy) {
        val restoreAction = policyToActions[restorePolicy]
        restoreAction?.invoke(taskDao)
    }
}


class Context{
    val pathToDb = "app.db"
}

class NitriteManager{
    companion object {

        @Volatile
        private var INSTANCE: Nitrite? = null

        fun getDBInstance(context: Context): Nitrite {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildNitriteDB(context).also { INSTANCE = it }
            }
        }

        private fun buildNitriteDB(context: Context): Nitrite {
            return nitrite {
                autoCommitBufferSize = 2048
                compress = true
                autoCompact = false
//                nitriteMapper = JacksonMapper()
//                file = File(context.pathToDb)
            }
        }
    }
}

