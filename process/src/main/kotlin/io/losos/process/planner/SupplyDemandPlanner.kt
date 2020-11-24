package io.losos.process.planner

import io.losos.platform.LososPlatform

/**
 * This is scratch idea, not implemented.
 * Should be implemented as a part of backpressure strategy as
 * 1) alternative implementation of subprocess planner
 * 2) planner underneath ServiceActionManager
 */

data class JobSupply(
    val type: String
//link to supplier
)

data class JobDemand(
    val type: String
//link to demander
)


/**
 * Supply-demand planner has to queue:
 * 1. Demand queue - it is usual queue of job request to be done, which is managed (ordered, cleaned up)
 *    according to some policies
 * 2. Supply queue - queue of work request
 *
 * Each time supply is received demand queue is searched for appropriate demand. If there is one -
 * the job is sent to the supplier.
 * Each time demand is received there is a backward logic.
 */
class SupplyDemandPlanner(
    val platform: LososPlatform,
    val demandPath: String
) {

    init {
        platform.subscribe(demandPath, JobDemand::class.java) {
            val demand = it.payload
        }
    }

    fun supply(js: JobSupply) {

    }

    fun demand(jd: JobDemand) {

    }

}