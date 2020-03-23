package net.mamoe.mirai.utils

import kotlin.js.Date

/**
 * JVM 控制台日志实现
 */
actual open class PlatformLogger constructor(
    override val identity: String? = "Mirai",
    open val output: (String) -> Unit
) : MiraiLoggerPlatformBase() {
    actual constructor(identity: String?) : this(identity, ::println)

    override fun verbose0(message: String?) = println(message, LoggerTextFormat.RESET)
    override fun verbose0(message: String?, e: Throwable?) {
        if (message != null) verbose(message.toString())
        e?.message?.let { verbose(it) }
        e?.cause?.let { verbose(it) }
    }

    override fun info0(message: String?) = println(message, LoggerTextFormat.LIGHT_GREEN)
    override fun info0(message: String?, e: Throwable?) {
        if (message != null) info(message.toString())
        e?.message?.let { info(it) }
        e?.cause?.let { info(it) }
    }

    override fun warning0(message: String?) = println(message, LoggerTextFormat.LIGHT_RED)
    override fun warning0(message: String?, e: Throwable?) {
        if (message != null) warning(message.toString())
        e?.message?.let { warning(it) }
        e?.cause?.let { warning(it) }
    }

    override fun error0(message: String?) = println(message, LoggerTextFormat.RED)
    override fun error0(message: String?, e: Throwable?) {
        if (message != null) error(message.toString())
        e?.message?.let { error(it) }
        e?.cause?.let { error(it) }
    }

    override fun debug0(message: String?) = println(message, LoggerTextFormat.LIGHT_CYAN)
    override fun debug0(message: String?, e: Throwable?) {
        if (message != null) debug(message.toString())
        e?.message?.let { debug(it) }
        e?.cause?.let { debug(it) }
    }

    private fun println(value: String?, color: LoggerTextFormat) {
        val date = Date()
        val time = date.getHours().toString() + ":" + date.getMinutes() + ":" + date.getSeconds()

        if (identity == null) {
            output("$color$time : $value")
        } else {
            output("$color$identity $time : $value")
        }
    }
}

/**
 * @author NaturalHG
 */
@Suppress("unused")
internal enum class LoggerTextFormat(private val format: String) {
    RESET("\u001b[0m"),

    WHITE("\u001b[30m"),
    RED("\u001b[31m"),
    EMERALD_GREEN("\u001b[32m"),
    GOLD("\u001b[33m"),
    BLUE("\u001b[34m"),
    PURPLE("\u001b[35m"),
    GREEN("\u001b[36m"),

    GRAY("\u001b[90m"),
    LIGHT_RED("\u001b[91m"),
    LIGHT_GREEN("\u001b[92m"),
    LIGHT_YELLOW("\u001b[93m"),
    LIGHT_BLUE("\u001b[94m"),
    LIGHT_PURPLE("\u001b[95m"),
    LIGHT_CYAN("\u001b[96m")
    ;

    override fun toString(): String = format
}