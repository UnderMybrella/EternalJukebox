package org.abimon.notifly

import org.abimon.visi.lang.ClassBuilder
import org.abimon.visi.lang.asOptional
import java.util.*

data class NotificationPayload(
        val title: String = "\u200B",
        val body: String = "\u200B",
        val sound: Optional<String> = Optional.empty(),
        val click_action: Optional<String> = Optional.empty(),

        val body_loc_key: Optional<String> = Optional.empty(),
        val body_loc_args: Optional<String> = Optional.empty(),
        val title_loc_key: Optional<String> = Optional.empty(),
        val title_loc_args: Optional<String> = Optional.empty(),

        //iOS
        val badge: Optional<String> = Optional.empty(),

        //Android
        val tag: Optional<String> = Optional.empty(),
        val color: Optional<String> = Optional.empty()
)

class NotificationBuilder: ClassBuilder<NotificationPayload>(NotificationPayload::class) {
    fun title(title: String) = put("title", title)
    fun body(body: String) = put("body", body)

    fun defaultSound() = sound("default")
    fun sound(sound: String) = sound(sound.asOptional())
    fun sound(sound: Optional<String>) = put("sound", sound)
}

fun notification(init: NotificationBuilder.() -> Unit): NotificationPayload {
    val builder = NotificationBuilder()
    builder.init()
    return builder()
}