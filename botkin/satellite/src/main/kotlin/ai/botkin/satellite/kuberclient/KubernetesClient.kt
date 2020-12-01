package ai.botkin.satellite.kuberclient



interface KubernetesClient{
    fun initClient()
    fun deletePod(namespace:String, podName:String):Boolean
}



