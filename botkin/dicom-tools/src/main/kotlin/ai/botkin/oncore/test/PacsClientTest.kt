package ai.botkin.oncore.test

import ai.botkin.oncore.dicom.PacsClient

interface PacsClientTest {

    fun withClient(client: PacsClient, block: (client: PacsClient) -> Unit) {
        try {
            block(client)
        } finally {
            client.shutdown()
        }
    }

}