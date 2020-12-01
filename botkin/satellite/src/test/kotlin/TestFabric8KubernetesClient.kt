import ai.botkin.satellite.kuberclient.Fabric8KubernetesClient

class TestFabric8KubernetesClient {
}

fun main() {
    val client = Fabric8KubernetesClient()
    client.deletePod("default", "pgadmin")

}