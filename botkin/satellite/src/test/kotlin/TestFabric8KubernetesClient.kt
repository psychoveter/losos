import botkin.ai.kuberclient.Fabric8KubernetesClient

class TestFabric8KubernetesClient {
}

fun main() {
    val client = Fabric8KubernetesClient()
    client.deletePod("default", "pgadmin")

}