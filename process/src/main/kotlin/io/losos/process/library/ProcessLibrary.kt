package io.losos.process.library

import io.losos.platform.LososPlatform
import io.losos.common.ProcessDef
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


interface ProcessLibrary {
    fun getAvailableProcesses(): Map<String, ProcessDef>
}


/**
 * Reads process defitions from etcd, where all defs are stored in separate keys.
 * Key name is equal to process name,
 * Value contains json description of ProcessDef
 */
class EtcdProcessLibrary(val platform: LososPlatform, val keyRoot: String): ProcessLibrary {

    override fun getAvailableProcesses(): Map<String, ProcessDef> {
        return platform
            .getPrefix(keyRoot, ProcessDef::class.java)
            .map { it.value.name to it.value }
            .toMap()
    }
}

/**
 * Loads available processes from specified resource folder
 */
class ResourceFolderProcessLibrary(val folderPath: String): ProcessLibrary {

    override fun getAvailableProcesses(): Map<String, ProcessDef> {
        val files = getResourceFiles(folderPath)

        TODO("not implemented")
    }

    private fun getResourceFiles(path: String): List<String> {
        val files = mutableListOf<String>()
        getResourceAsStream(path).use {
            BufferedReader( InputStreamReader(it) ).use {
                var resource: String? = null
                do {
                    resource = it.readLine()
                    if (resource != null)
                        files.add(resource)
                } while (resource != null)
            }
        }

        return files;
    }

    private fun readFileAsString(ins: InputStream) {

    }

    private fun getResourceAsStream(resource: String): InputStream? {
        val `in` = getContextClassLoader()!!.getResourceAsStream(resource)
        return `in` ?: ResourceFolderProcessLibrary::class.java.getResourceAsStream(resource)
    }

    private fun getContextClassLoader(): ClassLoader? {
        return Thread.currentThread().contextClassLoader
    }

}