package ai.botkin

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class PlatformControllerApp

fun main(args: Array<String>) {
    SpringApplication.run(PlatformControllerApp::class.java, *args)
}