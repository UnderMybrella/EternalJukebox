package org.abimon.eternalJukebox.data.analytics

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.eternalJukebox.simpleClassName
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object InfluxAnalyticsStorage : IAnalyticsStorage {
    val ip: String = EternalJukebox.config.analyticsStorageOptions["ip"] as? String ?: throw IllegalStateException()
    val db: String = URLEncoder.encode(EternalJukebox.config.analyticsStorageOptions["db"] as? String ?: throw IllegalStateException(), "UTF-8")
    val user: String? = EternalJukebox.config.analyticsStorageOptions["user"] as? String
    val pass: String? = EternalJukebox.config.analyticsStorageOptions["pass"] as? String

    override fun shouldStore(type: EnumAnalyticType<*>): Boolean = true
    override fun <T : Any> store(now: Long, data: T, type: EnumAnalyticType<T>): Boolean {
        val ns = TimeUnit.NANOSECONDS.convert(now, TimeUnit.MILLISECONDS)

        val postRequest = Fuel.post("${if (ip.indexOf("://") == -1) "http://" else ""}$ip/write?db=$db")
        if (user != null && pass != null)
            postRequest.authentication().basic(user, pass)

        val (_, response) = postRequest.body("eternal_jukebox ${type::class.simpleClassName.toLowerCase()}=$data $ns").response()
        return response.statusCode == 204
    }

    override fun storeMultiple(now: Long, data: List<Pair<EnumAnalyticType<*>, Any>>) {
        val ns = TimeUnit.NANOSECONDS.convert(now, TimeUnit.MILLISECONDS)

        val postRequest = Fuel.post("${if (ip.indexOf("://") == -1) "http://" else ""}$ip/write?db=$db")
        if (user != null && pass != null)
            postRequest.authentication().basic(user, pass)

        postRequest.body("eternal_jukebox ${data.joinToString(",") { (type, value) -> "${type::class.simpleClassName.toLowerCase()}=$value" }} $ns").response { _, _, _ ->  }
    }
}