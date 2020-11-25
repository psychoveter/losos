//import com.fasterxml.jackson.databind.SerializationFeature
//import io.ktor.application.*
//import io.ktor.client.*
//import io.ktor.client.engine.*
//import io.ktor.client.features.json.*
//import io.ktor.client.request.*
//import io.ktor.features.*
//import io.ktor.http.*
//import io.ktor.jackson.*
//import kotlinx.coroutines.runBlocking
//import botkin.ai.Done
//import botkin.ai.Ok
//import kotlin.test.assertEquals
//import kotlin.test.assertNotEquals
//
//class TestTasks {
//    val client = HttpClient(){
//        install(JsonFeature) {
//            serializer = JacksonSerializer()
//        }
//        }
//
//
//    suspend fun normalPipelineScenario() = run{
//        val workerTypes = listOf<String>("download", "ml", "sr", "sc", "upload")
//        for ((index, workerType) in workerTypes.withIndex()){
//            workerPipeline(workerType, index)
//        }
//
//
//    }
//    suspend fun workerPipeline(workerType:String, id:Int){
//        val getTaskOk = client.get<String>("http://127.0.0.1:8080/get_task/$workerType")
//        assertEquals("Ok", getTaskOk)
//
//        //task with id 1 has been created
//        val submitTaskOk = client.post<String>{
//            url("http://127.0.0.1:8080/ok")
//            contentType(ContentType.botkin.ai.Application.Json)
//            body=Ok(workerType, id)
//        }
//        assertEquals("Ok", submitTaskOk)
//
//        val DoneTaskOk = client.post<String>{
//            url("http://127.0.0.1:8080/done")
//            contentType(ContentType.botkin.ai.Application.Json)
//            body=Done(workerType, id, "Ok")
//        }
//        assertEquals("Ok", DoneTaskOk)
//
//    }
//}
//
//fun botkin.ai.main()  = runBlocking {
//    TestTasks().normalPipelineScenario()
//}