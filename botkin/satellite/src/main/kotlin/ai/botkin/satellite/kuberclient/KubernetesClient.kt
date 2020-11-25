package botkin.ai.kuberclient



interface KubernetesClient{
    fun initClient()
    fun deletePod(namespace:String, podName:String):Boolean
}



