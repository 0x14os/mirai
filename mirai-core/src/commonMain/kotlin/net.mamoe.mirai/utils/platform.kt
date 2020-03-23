/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "NOTHING_TO_INLINE")

package net.mamoe.mirai.utils

import io.ktor.client.HttpClient
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * 时间戳
 */
expect val currentTimeMillis: Long

inline val currentTimeSeconds: Long get() = currentTimeMillis / 1000

/**
 * 仅供内部使用的工具类.
 * 不写为扩展是为了避免污染命名空间.
 */
@MiraiInternalAPI
expect object MiraiPlatformUtils {
    @JvmStatic
    @JvmOverloads
    fun unzip(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ByteArray

    @JvmStatic
    @JvmOverloads
    fun zip(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ByteArray


    @JvmStatic
    @JvmOverloads
    fun md5(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ByteArray

    @JvmStatic
    inline fun md5(str: String): ByteArray

    @JvmStatic
    fun localIpAddress(): String

    /**
     * Ktor HttpClient. 不同平台使用不同引擎.
     */
    @JvmStatic
    @MiraiInternalAPI
    val Http: HttpClient
}


@Suppress("DuplicatedCode") // false positive. `this` is not the same for `List<Byte>` and `ByteArray`
internal fun ByteArray.checkOffsetAndLength(offset: Int, length: Int) {
    require(offset >= 0) { "offset shouldn't be negative: $offset" }
    require(length >= 0) { "length shouldn't be negative: $length" }
    require(offset + length <= this.size) { "offset ($offset) + length ($length) > array.size (${this.size})" }
}