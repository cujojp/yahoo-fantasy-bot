package com.landonpatmore.yahoofantasybot.backend

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

fun main() {
    // Start Koin first
    org.koin.core.context.startKoin {
        modules(com.landonpatmore.yahoofantasybot.shared.modules.sharedModule)
    }

    val keyStoreFile = File("certificates/keystore.p12")
    if (!keyStoreFile.exists()) {
        println("ERROR: SSL certificate not found at certificates/keystore.p12")
        println("Please run: ./scripts/setup-local-https.sh")
        return
    }

    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(FileInputStream(keyStoreFile), "changeit".toCharArray())

    val environment = applicationEngineEnvironment {
        connector {
            port = 8080
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = "localhost",
            keyStorePassword = { "changeit".toCharArray() },
            privateKeyPassword = { "changeit".toCharArray() }
        ) {
            port = 8443
            keyStorePath = keyStoreFile
        }
        module {
            module()
        }
    }

    embeddedServer(Netty, environment).start(wait = true)
}
