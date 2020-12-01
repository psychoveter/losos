
import ai.botkin.satellite.kuberclient.OfficialKubernetesClient


class TestOfficialKubernetesClient {

}

fun main() {
    val pods = OfficialKubernetesClient().getPods()
    for (item in pods.items) {
        println(item.metadata!!.name)
    }
}