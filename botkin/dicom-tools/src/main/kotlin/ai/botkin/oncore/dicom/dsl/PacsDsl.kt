package ai.botkin.oncore.dicom.dsl

import ai.botkin.oncore.dicom.util.LoggingAssociationMonitor
import ai.botkin.oncore.dicom.PacsClient
import ai.botkin.oncore.dicom.service.MoveSCUService
import ai.botkin.oncore.dicom.service.StoreSCPService
import org.dcm4che3.data.Keyword
import org.dcm4che3.net.ApplicationEntity
import org.dcm4che3.net.Connection
import org.dcm4che3.net.Device
import java.util.concurrent.Executors


/**
 * DSL for client definition.
 *
 * Notice: this DSL may be used as external script for client configuration and loaded at runtime.
 * https://stackoverflow.com/questions/34974039/how-can-i-run-kotlin-script-kts-files-from-within-kotlin-java
 */
object PacsClientDsl {

    operator fun invoke(init: PacsClientContextDsl.() -> Unit): PacsClient {
        val dslDef = PacsClientContextDsl().apply(init)
        val dicomFactory = DicomFactory(dslDef)
        return dicomFactory.buildClient()
    }

}

@GWDslMarker
class PacsClientContextDsl {

    private val presentationContexts = mutableListOf<PresentationContextDsl>()

    lateinit var device: DeviceDsl

    var ae: AEDsl? = null
        private set

    var targetPacs: PACSDsl? = null
        private set

    var findScu: FindSCUDsl? = null
        private set
    var ssl: SSLDsl? = null
        private set
    var depers: DepersonalizationDsl? = null
        private set
    var storeScu: StoreSCUDsl? = null
        private set
    var storeScp: StoreSCPDsl? = null
        private set
    var getScu: GetSCUDsl? = null
        private set
    var moveScu: MoveSCUDsl? = null
        private set

    //--accessors-------------------------------------------------------------------------------------------------------

    fun getPresentationContexts() = presentationContexts.toList()

    //--dsl-methods-----------------------------------------------------------------------------------------------------
    fun PresentationContext(init: PresentationContextDsl.() -> Unit) {
        presentationContexts.add(PresentationContextDsl().apply(init))
    }

    fun PACS(init: PACSDsl.() -> Unit) {
        targetPacs = PACSDsl().apply(init)
    }

    fun ApplicationEntity(init: AEDsl.() -> Unit) {
        ae = AEDsl().apply(init)
    }

    fun SSL(init: SSLDsl.() -> Unit) {
        ssl = SSLDsl().apply(init)
    }

    fun Depersonalization(init: DepersonalizationDsl.() -> Unit) {
        depers = DepersonalizationDsl().apply(init)
    }


    fun FindSCU(init: FindSCUDsl.() -> Unit) {
        findScu = FindSCUDsl().apply(init)
    }

    fun GetSCU(init: GetSCUDsl.() -> Unit) {
        getScu = GetSCUDsl().apply(init)
    }

    fun MoveSCU(init: MoveSCUDsl.() -> Unit) {
        moveScu = MoveSCUDsl().apply(init)
    }

    /**
     * Configures StoreSCUSerivce.
     * PACS server configured by PACS{} method is used as target
     */
    fun StoreSCU(init: StoreSCUDsl.() -> Unit) {
        storeScu = StoreSCUDsl().apply(init)
    }

    /**
     * Configures StoreSCPService
     */
    fun StoreSCP(init: StoreSCPDsl.() -> Unit) {
        storeScp = StoreSCPDsl().apply(init)
    }

    fun Device(init: DeviceDsl.() -> Unit) {
        device = DeviceDsl().apply(init)
    }


}

@GWDslMarker
class FindSCUDsl {}

@GWDslMarker
class GetSCUDsl {}

@GWDslMarker
class MoveSCUDsl {

    fun build(): MoveSCUService {
        val service = MoveSCUService()

        return service
    }
}



@GWDslMarker
class StoreSCUDsl {}

@GWDslMarker
class StoreSCPDsl {

    lateinit var ae: AEDsl
    lateinit var storeFolder: String

    fun ApplicationEntity(init: AEDsl.() -> Unit) {
        ae = AEDsl().apply(init)
    }

}

@GWDslMarker
class DepersonalizationDsl {
    lateinit var storagePath: String
    lateinit var strategy: String
    private val _tags = mutableListOf<Int>()

    fun getTags(): IntArray = _tags.toIntArray()

    fun tags(vararg tgs: Int) {
        tgs.forEach {
            if (Keyword.valueOf(it).isEmpty())
                throw GatewayConfigurationException("Tag $it doesn't exist")
        }
        _tags.addAll(tgs.toList())
    }

}

@GWDslMarker
class SSLDsl {
    var keyType: String = "JKS"
    var keyStoreFile: String = "cacecret.jks"
    var keyStorePassword: String = "password"
    var keyStoreSecret: String = "secret"
    var trustType: String = "JKS"
    var trustStoreFile: String = "cacerts.jks"
    var trustStorePassword: String = "secret"
    var tlsCipherSuites = arrayOf("SSL_RSA_WITH_NULL_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "SSL_RSA_WITH_3DES_EDE_CBC_SHA")
    var tlsProtocol = arrayOf("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2")
}

@GWDslMarker
class ConnectionDsl {
    lateinit var host: String
    var port: Int = 5999
    var connectionTimeout = 1000
    var acceptTimeout = 3000000
    var requestTimeout = 3000000
    var responseTimeout = 3000000
    var releaseTimeout = 3000000
    var sendTimeout = 3000000
    var retrieveTimeout = 3000000
    var storeTimeout = 3000000

    fun build(): Connection {
        val connection = Connection()
        connection.port = port
        connection.hostname = host
        connection.connectTimeout = connectionTimeout
        connection.acceptTimeout = acceptTimeout
        connection.requestTimeout = requestTimeout
        connection.releaseTimeout = releaseTimeout
        connection.sendTimeout = sendTimeout
        connection.storeTimeout = storeTimeout
        connection.responseTimeout = responseTimeout
        connection.retrieveTimeout = retrieveTimeout
        return connection
    }
}

@GWDslMarker
class AEDsl {
    lateinit var conn: ConnectionDsl
    lateinit var aeTitle: String


    fun Connection(init: ConnectionDsl.() -> Unit) {
        conn = ConnectionDsl().apply(init)
    }

    fun build(
        device: Device,
        providedConnection: Connection? = null
    ): ApplicationEntity {
        val conn: Connection =
            providedConnection ?: conn.build()
            ?: throw GatewayConfigurationException("You should either provided connection or configure it")

        val ae = ApplicationEntity(aeTitle)
        device.addConnection(conn)
        device.addApplicationEntity(ae)
        ae.addConnection(conn)
        return ae
    }

}

@GWDslMarker
class DeviceDsl {
    lateinit var name: String
    var threads: Int = 3


    fun build(): Device {
        val device = Device(name)
        device.associationMonitor = LoggingAssociationMonitor(device)
        device.executor = Executors.newFixedThreadPool(threads)
        device.scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        return device
    }
}

@GWDslMarker
class PACSDsl {
    lateinit var aeTitle: String
    lateinit var conn: ConnectionDsl

    fun Connection(init: ConnectionDsl.() -> Unit) {
        conn = ConnectionDsl().apply(init)
    }

}

@GWDslMarker
class PresentationContextDsl {
    lateinit var abstractSyntax: String
    val _transferSyntax = mutableListOf<String>()
    var isSCU: Boolean = false
    var isSCP: Boolean = false

    fun transferSyntax(vararg ts: String) {
        _transferSyntax.addAll(ts)
    }

}

class GatewayConfigurationException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?, cause: Throwable?, enableSuppression: Boolean, writableStackTrace: Boolean) :
        super(message, cause, enableSuppression, writableStackTrace)
}
