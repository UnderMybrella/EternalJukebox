package org.abimon.eternalJukebox

import java.io.File
import java.util.concurrent.TimeUnit

object MediaWrapper {
    object ffmpeg {
        val installed: Boolean
            get() {
                val process = ProcessBuilder().command("ffmpeg", "-version").start()

                process.waitFor(5, TimeUnit.SECONDS)

                return String(process.inputStream.readBytes(), Charsets.UTF_8).startsWith("ffmpeg version")
            }

        fun convert(input: File, output: File, error: File): Boolean {
            val ffmpegProcess = ProcessBuilder().command("ffmpeg", "-i", input.absolutePath, output.absolutePath).redirectErrorStream(true).redirectOutput(error).start()

            return ffmpegProcess.waitFor(60, TimeUnit.SECONDS)
        }
    }
}