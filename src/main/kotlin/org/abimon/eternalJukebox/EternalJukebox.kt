package org.abimon.eternalJukebox

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import org.abimon.eternalJukebox.data.analysis.IAnalyser
import org.abimon.eternalJukebox.data.analysis.SpotifyAnalyser
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.eternalJukebox.data.storage.LocalStorage
import org.abimon.eternalJukebox.handlers.StaticResources
import org.abimon.eternalJukebox.handlers.api.AnalysisAPI
import org.abimon.eternalJukebox.handlers.api.IAPI
import org.abimon.eternalJukebox.objects.JukeboxConfig
import org.abimon.visi.io.println
import java.io.File
import java.util.function.Consumer
import kotlin.reflect.KFunction

object EternalJukebox {
    val jsonMapper: ObjectMapper = ObjectMapper()
            .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

    val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

    val jsonConfig: File = File("config.json")
    val yamlConfig: File = File("config.yaml")

    val config: JukeboxConfig
    val vertx: Vertx
    val webserver: HttpServer

    val storage: IStorage

    val spotify: IAnalyser = SpotifyAnalyser

    val apis: Array<IAPI> = arrayOf(
        AnalysisAPI
    )

    fun start() {
        webserver.listen(config.port)
        println("Now listening on port ${config.port}")

        spotify.search("Never Gonna Give You Up").first().url.println()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        EternalJukebox.start()
    }

    init {
        if(jsonConfig.exists())
            config = jsonMapper.readValue(jsonConfig, JukeboxConfig::class.java)
        else if(yamlConfig.exists())
            config = yamlMapper.readValue(yamlConfig, JukeboxConfig::class.java)
        else
            config = JukeboxConfig()

        println("Loaded config: $config")

        vertx = Vertx.vertx()
        webserver = vertx.createHttpServer()

        val mainRouter = Router.router(vertx)

        val apiRouter = Router.router(vertx)
        apis.filter { it.name !in config.disabledAPIs }.forEach { api ->
            val sub = Router.router(vertx)
            api.setup(sub)
            apiRouter.mountSubRouter(api.mountPath, sub)
        }
        mainRouter.mountSubRouter("/api", apiRouter)

        StaticResources.setup(mainRouter)

        webserver.requestHandler(mainRouter::accept)

        // Config Handling

        when(config.storageType.toUpperCase()) {
            "LOCAL" -> storage = LocalStorage
            else -> storage = LocalStorage
        }
    }

    fun <T: Any> KFunction<*>.bind(param: Any): Consumer<in T> {
        return Consumer { this.call(it, param) }
    }
}