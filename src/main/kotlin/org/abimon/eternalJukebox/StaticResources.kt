package org.abimon.eternalJukebox

import io.vertx.core.Handler
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.FaviconHandler
import java.io.File

object StaticResources {
    val faviconHandler: FaviconHandler by lazy { FaviconHandler.create("files/favicon.png") }
    val ourJar = File(StaticResources::class.java.protectionDomain.codeSource.location.file)

    fun serveStatic(file: File): Handler<RoutingContext> = Handler { context -> context.ifFileNotCached(file) { response().httpsOnly().htmlContent().sendCachedFile(it) } }
    fun serveStaticAndPopular(file: File, type: String): Handler<RoutingContext> = Handler { context ->
        context.ifFileNotCached(file) { response().httpsOnly().htmlContent().sendCachedFile(it) }
        when(type) {
            "jukebox" -> populariseJukebox(context)
            "canonizer" -> populariseCanonizer(context)
        }
    }

    fun setup(router: Router) {
        router.get("/jukebox_index.html").handler(serveStatic(File("jukebox_index.html")))
        router.get("/jukebox_search.html").handler(serveStatic(File("jukebox_search.html")))
        router.get("/jukebox_go.html").handler(serveStaticAndPopular(File("jukebox_go.html"), "jukebox"))
        router.get("/faq.html").handler(serveStatic(File("faq.html")))

        router.get("/canonizer_index.html").handler(serveStatic(File("canonizer_index.html")))
        router.get("/canonizer_search.html").handler(serveStatic(File("canonizer_search.html")))
        router.get("/canonizer_go.html").handler(serveStaticAndPopular(File("canonizer_go.html"), "canonizer"))

        router.get("/retro_index.html").handler(serveStatic(File("retro_index.html")))
        router.get("/retro_faq.html").handler(serveStatic(File("retro_faq.html")))

        router.get("/files/*").handler(StaticFileHandler("/files", File("files")))

        if(ourJar.extension == "jar")
            router.get("/built.jar").handler { context -> context.response().putHeader("Content-Disposition", "attachment;filename=EternalJukebox.jar").sendFile(ourJar.absolutePath) }

        router.get("/healthy").handler { context -> context.response().setStatusCode(200).end() }

        router.get("/robots.txt").handler { context -> context.response().textContent().end("User-agent: *\nDisallow:") }
        router.get("/favicon.ico").handler(faviconHandler)
    }
}