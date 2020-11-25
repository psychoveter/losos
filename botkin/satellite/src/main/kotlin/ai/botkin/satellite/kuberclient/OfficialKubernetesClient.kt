package botkin.ai.kuberclient

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.util.Config

class OfficialKubernetesClient:KubernetesClient{

    override fun initClient() {
        val client: ApiClient = Config.defaultClient()
        Configuration.setDefaultApiClient(client)
    }

    fun getPods(): V1PodList {
        initClient()
        val api = CoreV1Api()
        return api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null)
    }

    override fun deletePod(namespace:String, podName:String):Boolean{
        initClient()
        return false
    }
}