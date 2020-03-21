@file:Suppress("unused")

package net.mamoe.mirai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.io.ByteReadChannel
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.data.AddFriendResult
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.network.BotNetworkHandler
import net.mamoe.mirai.network.LoginFailedException
import net.mamoe.mirai.utils.*

/**
 * 机器人对象. 一个机器人实例登录一个 QQ 账号.
 * Mirai 为多账号设计, 可同时维护多个机器人.
 *
 * 注: Bot 为全协程实现, 没有其他任务时若不使用 [join], 主线程将会退出.
 *
 * @see Contact 联系人
 * @see kotlinx.coroutines.isActive 判断 [Bot] 是否正常运行中. (在线, 且没有被 [close])
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@OptIn(
    MiraiInternalAPI::class, LowLevelAPI::class, MiraiExperimentalAPI::class, JavaHappyAPI::class
)
actual abstract class Bot actual constructor() : CoroutineScope, LowLevelBotAPIAccessor, BotJavaHappyAPI() {
    actual companion object {
        /**
         * 复制一份此时的 [Bot] 实例列表.
         */
        actual val instances: List<WeakRef<Bot>>
            get() = BotImpl.instances.toList()

        /**
         * 遍历每一个 [Bot] 实例
         */
        actual inline fun forEachInstance(block: (Bot) -> Unit) = BotImpl.forEachInstance(block)

        /**
         * 遍历每一个 [Bot] 实例
         */
        @JavaHappyAPI
        @Suppress("FunctionName")
        fun __forEachInstanceForJava__(block: (Bot) -> Unit) = forEachInstance(block)

        /**
         * 获取一个 [Bot] 实例, 找不到则 [NoSuchElementException]
         */
        actual fun getInstance(qq: Long): Bot = BotImpl.getInstance(qq = qq)
    }

    /**
     * [Bot] 运行的 [Context].
     *
     * 在 JVM 的默认实现为 [net.mamoe.mirai.utils.Context]
     * 在 Android 实现为 `android.content.Context`
     */
    actual abstract val context: Context

    /**
     * QQ 号码. 实际类型为 uint
     */
    actual abstract val uin: Long

    /**
     * 昵称
     */
    @MiraiExperimentalAPI("还未支持")
    actual val nick: String
        get() = ""// TODO("bot 昵称获取")

    /**
     * 日志记录器
     */
    actual abstract val logger: MiraiLogger

    // region contacts

    actual abstract val selfQQ: QQ

    /**
     * 机器人的好友列表. 它将与服务器同步更新
     */
    @Deprecated(
        "use friends instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("this.friends")
    )
    actual abstract val qqs: ContactList<QQ>

    /**
     * 机器人的好友列表. 它将与服务器同步更新
     */
    actual abstract val friends: ContactList<QQ>

    /**
     * 获取一个好友或一个群.
     */
    @Deprecated(
        "use getFriend or getGroup instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("this.qqs.getOrNull(id) ?: this.groups.getOrNull(id) ?: throw NoSuchElementException(\"contact id \$id\")")
    )
    actual operator fun get(id: Long): Contact {
        return this.friends.getOrNull(id) ?: this.groups.getOrNull(id) ?: throw NoSuchElementException("contact id $id")
    }

    /**
     * 判断是否有这个 id 的好友或群.
     * 在一些情况下这可能会造成歧义. 请考虑后使用.
     */
    actual operator fun contains(id: Long): Boolean {
        return this.friends.contains(id) || this.groups.contains(id)
    }

    /**
     * 获取一个好友对象. 若没有这个好友, 则会抛出异常 [NoSuchElementException]
     */
    actual fun getFriend(id: Long): QQ {
        if (id == uin) return selfQQ
        return friends.delegate.getOrNull(id)
            ?: throw NoSuchElementException("No such friend $id for bot ${this.uin}")
    }

    /**
     * 机器人加入的群列表.
     */
    actual abstract val groups: ContactList<Group>

    /**
     * 获取一个机器人加入的群.
     *
     * @throws NoSuchElementException 当不存在这个群时
     */
    actual fun getGroup(id: Long): Group {
        return groups.delegate.getOrNull(id)
            ?: throw NoSuchElementException("No such group $id for bot ${this.uin}")
    }

    // endregion

    // region network

    /**
     * 网络模块
     */
    actual abstract val network: BotNetworkHandler

    /**
     * 挂起协程直到 [Bot] 下线.
     */
    actual suspend inline fun join() = network.join()

    /**
     * 登录, 或重新登录.
     * 这个函数总是关闭一切现有网路任务, 然后重新登录并重新缓存好友列表和群列表.
     *
     * 一般情况下不需要重新登录. Mirai 能够自动处理掉线情况.
     *
     * 最终调用 [net.mamoe.mirai.network.BotNetworkHandler.relogin]
     *
     * @throws LoginFailedException
     */
    actual abstract suspend fun login()
    // endregion


    // region actions

    /**
     * 撤回这条消息. 可撤回自己 2 分钟内发出的消息, 和任意时间的群成员的消息.
     *
     * [Bot] 撤回自己的消息不需要权限.
     * [Bot] 撤回群员的消息需要管理员权限.
     *
     * @param source 消息源. 可从 [MessageReceipt.source] 获得, 或从消息事件中的 [MessageChain] 获得.
     *
     * @throws PermissionDeniedException 当 [Bot] 无权限操作时
     *
     * @see Bot.recall (扩展函数) 接受参数 [MessageChain]
     * @see _lowLevelRecallFriendMessage 低级 API
     * @see _lowLevelRecallGroupMessage 低级 API
     */
    actual abstract suspend fun recall(source: MessageSource)

    /**
     * 获取图片下载链接
     */
    actual abstract suspend fun queryImageUrl(image: Image): String

    /**
     * 获取图片下载链接并开始下载.
     *
     * @see ByteReadChannel.copyAndClose
     * @see ByteReadChannel.copyTo
     */
    actual abstract suspend fun openChannel(image: Image): ByteReadChannel

    /**
     * 添加一个好友
     *
     * @param message 若需要验证请求时的验证消息.
     * @param remark 好友备注
     */
    @MiraiExperimentalAPI("未支持")
    actual abstract suspend fun addFriend(id: Long, message: String?, remark: String?): AddFriendResult

    // endregion

    /**
     * 关闭这个 [Bot], 立即取消 [Bot] 的 [kotlinx.coroutines.SupervisorJob].
     * 之后 [kotlinx.coroutines.isActive] 将会返回 `false`.
     *
     * **注意:** 不可重新登录. 必须重新实例化一个 [Bot].
     *
     * @param cause 原因. 为 null 时视为正常关闭, 非 null 时视为异常关闭
     *
     * @see closeAndJoin 取消并 [Bot.join], 以确保 [Bot] 相关的活动被完全关闭
     */
    actual abstract fun close(cause: Throwable?)

    @OptIn(LowLevelAPI::class, MiraiExperimentalAPI::class)
    actual final override fun toString(): String = "Bot(${uin})"
}