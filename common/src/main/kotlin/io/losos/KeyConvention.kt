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


    fun keyNodeRegistry(nodeName: String) = "$NODE_REGISTRY_ROOT/$nodeName"
    fun keyNodeLease(nodeName: String) = "$NODE_LEASE_ROOT/$nodeName"
    fun keyNodeLibrary(nodeName: String) = "$NODE_LIBRARY_ROOT/$nodeName"
    fun keyProcessLibrary(nodeName: String, procName: String) = "${keyNodeLibrary(nodeName)}/$procName"
    fun keyProcessRegistry(nodeName: String) = "/proc/$nodeName/registry"
    fun keyProcessEntry(node: String, pid: String) = "${keyProcessRegistry(node)}/$pid"
    fun keyProcessState(node: String, pid: String) = "/proc/$node/state/$pid"

}