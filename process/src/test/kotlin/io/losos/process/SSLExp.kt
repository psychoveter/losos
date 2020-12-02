package io.losos.process

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

fun main(args: Array<String>) {
    val key = String(
        Files.readAllBytes(File("/home/veter/braingarden/onko/repos/etcd/docker/pkcs8-key.pem").toPath()),
        Charset.defaultCharset()
    )

    val privateKeyPEM = key
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace(System.lineSeparator().toRegex(), "")
        .replace("-----END PRIVATE KEY-----", "")

    val encoded: ByteArray = Base64.getDecoder().decode(privateKeyPEM)

    val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(encoded)
    val kk = keyFactory.generatePrivate(keySpec)

}