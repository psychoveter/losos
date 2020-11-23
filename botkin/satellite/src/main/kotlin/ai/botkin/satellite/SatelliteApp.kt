package ai.botkin.satellite

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class SatelliteApp

fun main(args: Array<String>) {
    SpringApplication.run(SatelliteApp::class.java, *args)
}