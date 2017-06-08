package org.abimon.eternalJukebox

import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.RoutingContext
import org.abimon.visi.io.iterate
import java.io.File

class StaticFileHandler(val endpoint: String, val root: File) : Handler<RoutingContext> {
    companion object {
        val instances = ArrayList<StaticFileHandler>()

        fun reload() {
            instances.forEach { instance ->
                instance.subfiles.clear()
                instance.subfiles.putAll(instance.root.iterate(true).map { subfile -> subfile.absolutePath.replace(instance.root.absolutePath + File.separator, "").toLowerCase() to subfile })
            }
        }
    }

    val subfiles = root.iterate(true).map { subfile -> subfile.absolutePath.replace(root.absolutePath + File.separator, "").toLowerCase() to subfile }.toMap(HashMap<String, File>())

    override fun handle(context: RoutingContext) {
        if (context.request().path() == endpoint || context.request().path() == "$endpoint/")
            return context.ifDataNotCached("<html><body><h1>Files in ${root.name}</h1><ul>${root.listFiles().filter { subFile -> !subFile.name.startsWith(".") }.joinToString("") { "<a href=\"$endpoint${it.absolutePath.replace(root.absolutePath, "")}\"><li>${it.name}</li></a>" }}</ul></body></html>") { response().httpsOnly().htmlContent().sendCachedData(it) }

        val path = context.request().path().toLowerCase()
        val relPath = path.replaceFirst("/$endpoint/", "")
        val file = subfiles[relPath]
        if (file != null) {
            if (file.isDirectory) {
                if (File(file, "index.html").exists())
                    return context.response().httpsOnly().redirect("/$endpoint${File(file, "index.html").absolutePath.replace(root.absolutePath, "").toLowerCase()}")
                else
                    return context.ifDataNotCached("<html><body><h1>Files in ${file.absolutePath.replace(root.absolutePath, "")}</h1><ul>${file.listFiles().filter { subFile -> !subFile.name.startsWith(".") }.joinToString("") { "<a href=\"$endpoint${it.absolutePath.replace(root.absolutePath, "").toLowerCase()}\"><li>${it.name}</li></a>" }}</ul></body></html>") { response().httpsOnly().htmlContent().sendCachedData(it) }
            } else {
                context.ifFileNotCached(file) { response().httpsOnly().sendCachedFile(file) }
                return
            }
        }

        if (path.child == "files.json") {
            val parentRelPath = path.parents.replaceFirst("/$endpoint/", "")
            val parentFile = subfiles[parentRelPath]
            if (parentFile != null)
                return context.ifDataNotCached(JsonArray(parentFile.listFiles().filter { subFile -> !subFile.isHidden && !subFile.name.startsWith(".") }.map { "$endpoint${it.absolutePath.replace(root.absolutePath, "").toLowerCase()}" }).toString()) { response().httpsOnly().jsonContent().sendCachedData(it) }
        }

        context.next()
    }

    init {
        instances.add(this)
    }

    val String.parents: String
        get() = if(this.lastIndexOf('/') == -1) "" else this.substring(0, this.lastIndexOf('/'))

    val String.child: String
        get() = if(this.lastIndexOf('/') == -1) this else this.substring(this.lastIndexOf('/') + 1, length)

    val String.extension: String
        get() = if(this.lastIndexOf('.') == -1) this else this.substring(this.lastIndexOf('.') + 1, length)
}