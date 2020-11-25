import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.4.0"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.spring") version "1.4.10"
    application
}
group = "me.valentin"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlinx")
    }
    maven {
        url = uri("https://www.dcm4che.org/maven2/")
    }
}
dependencies {
    testImplementation(kotlin("test-junit"))
    //JetBrains Dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")


    //Spring Dependencies
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    //swagger
//    implementation("io.springfox:springfox-swagger2:2.9.2")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("io.springfox:springfox-swagger-ui:3.0.0")

    //Nosql Database Nitrite
    implementation("org.dizitart:potassium-nitrite:2.1.0")

    //Logging
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ch.qos.logback:logback-core:1.2.3")

    // Opentracing implementation by Jaeger
    implementation("io.jaegertracing:jaeger-client:1.5.0")
    implementation("io.opentracing.contrib:opentracing-spring-web-starter:4.1.0")

    //Dcm4che
    implementation("org.dcm4che:dcm4che-imageio:3.3.8")
    implementation("org.dcm4che:dcm4che-core:3.3.8")

    //Kubernetes clients
    implementation("io.kubernetes:client-java:10.0.0")
    implementation("io.fabric8:kubernetes-client:4.12.0")

}

// nitrite restrictions
dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:2.9.5")
    }
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
application {
    mainClassName = "ai.botkin.satellite.SatelliteApplication"
}