package io.losos


/**
 * ---------------------------------------------------------------------------------------------------------------------
 *  Event path               |  \Event payload
 * ------------------------------------------------------------------------------------------------------------------------
 * /idseq                    | ???
 *   /<pool>                 | { pool_id, last_id }
 * ------------------------------------------------------------------------------------------------------------------------
 * /node                     |
 *   /registry               | { name: "string", service_registry }
 *      /<node-name>         | NodeInfo
 *   /lease                  |
 *      /<node-name>         |
 *   /library                |
 *      /<node-name>         |
 *         /<def name>       | ProcessDef
------------------------------------------------------------------------------------------------------------------------
 * /proc                     |
 *    /<node>                |
 *       /lease              |
 *          /<pid>           |
 *       /registry           |
 *          /<pid>           |  { pid: "string", type: "string", ... version, etc ... }
 *                           |
 *       /state              |
 *          /<pid>           |
 *       /action             |
 *          /<pid>           |
 *             /<aid>        | { id: "string", type: "string", "params": {} }
 *       /guard              |
 *          /<pid>           |
 *             /<gid>        |
 *                /<cnt>     |  { guard_id, counter, state, slots }
 *                           |
 * ------------------------------------------------------------------------------------------------------------------------
 * /invoke                   |
 * /async                 |
 * /<node>              |
 * <iid>              |  { iid: "string", clazz: "invocation class", params: {arbitrary object} }
 * /service               |
 * |  тут какой-то планировщик должен быть...
 * |
 * /subprocess            |
 * /<spid>              |  { spid: "string", "node": <executor_node_id>, "pid": "string" }
 * |
 * ------------------------------------------------------------------------------------------------------------------------
 * /agent                    |
 * /registry              |
 * /<agent_id>         |  { agent_id, descriptor }
 * /lease                 |  {}
 * /<agent_id>         |
 * /task                  |
 * /<agent_id>         |
 * /<task_id>     |  { task_id, payload }
 * ------------------------------------------------------------------------------------------------------------------------
 * /event                    |
 * /<event_type>          |
 * /<event_id>        | {arbitrary json event content}
*/
object KeyConvention {

    val NODE_REGISTRY_ROOT = "/node/registry"
    val NODE_LIBRARY_ROOT = "/node/library"
    val NODE_LEASE_ROOT = "/node/lease"


    fun keyNodeRegistry(node: String)                             = "/node/registry/$node"
    fun keyNodeLease(node: String)                                = "/node/lease/$node"
    fun keyNodeLibrary(node: String)                              = "/node/library/$node"
    fun keyNodeLibraryEntry(node: String, proc: String)           = "/node/library/$node/$proc"

    fun keyProcessRegistry(node: String)                          = "/proc/$node/registry"
    fun keyProcessEntry(node: String, pid: String)                = "/proc/$node/registry/$pid"
    fun keyProcessState(node: String, pid: String)                = "/proc/$node/state/$pid"
    fun keyAction(node: String, pid: String, defId: String)       = "/proc/$node/state/$pid/action/$defId"
    fun keyGuard(node: String, pid: String, defId: String)        = "/proc/$node/state/$pid/guard/$defId"

    fun keyInvocationEvent(node: String, pid: String, guardId: String, slotId: String)
                                                                  = "/proc/$node/state/$pid/invoke/$guardId/$slotId"


}

