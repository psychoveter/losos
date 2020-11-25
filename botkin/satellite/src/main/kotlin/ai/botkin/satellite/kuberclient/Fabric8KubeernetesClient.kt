package botkin.ai.kuberclient

import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient

class Fabric8KubernetesClient(config: Config? = null):KubernetesClient{
    private val client = if (config != null) DefaultKubernetesClient(config) else DefaultKubernetesClient()

    override fun initClient() {
        TODO("Not yet implemented")
    }

    override fun deletePod(namespace:String, podName:String):Boolean {
        val pod = listPods().items.filter{
                pod -> pod.metadata.name.contains(podName)
        }.firstOrNull()
        if(pod != null){
            println(pod.metadata.name)
            val res  = client.pods().inNamespace(namespace).withName(pod.metadata.name).delete()
            return if (res) {

                println("Pod has been deleted")
                true
            } else{
                println("Could not delete pod with name ${pod.metadata.name}")
                false
            }
        }

        else{
            println("There is no pod which name contains pgadmin")
            return false
        }

    }

    fun listPods():PodList{
        return client.pods().list()
    }

}