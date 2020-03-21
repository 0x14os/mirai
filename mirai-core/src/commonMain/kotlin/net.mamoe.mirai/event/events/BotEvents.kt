/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused")

package net.mamoe.mirai.event.events

import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.data.Packet
import net.mamoe.mirai.event.AbstractCancellableEvent
import net.mamoe.mirai.event.BroadcastControllable
import net.mamoe.mirai.event.CancellableEvent
import net.mamoe.mirai.event.events.ImageUploadEvent.Failed
import net.mamoe.mirai.event.events.ImageUploadEvent.Succeed
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.utils.ExternalImage
import net.mamoe.mirai.utils.MiraiExperimentalAPI

@Suppress("unused")
class EventCancelledException : RuntimeException {
    constructor()
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
    constructor(cause: Throwable?)
}

// note: 若你使用 IntelliJ IDEA, 按 alt + 7 可打开结构


// region Bot 状态

/**
 * [Bot] 登录完成, 好友列表, 群组列表初始化完成
 */
data class BotOnlineEvent(override val bot: Bot) : BotActiveEvent

/**
 * [Bot] 离线.
 */
sealed class BotOfflineEvent : BotEvent {

    /**
     * 主动离线
     */
    data class Active(override val bot: Bot, val cause: Throwable?) : BotOfflineEvent(), BotActiveEvent

    /**
     * 被挤下线
     */
    data class Force(override val bot: Bot, val title: String, val message: String) : BotOfflineEvent(), Packet,
        BotPassiveEvent

    /**
     * 被服务器断开或因网络问题而掉线
     */
    data class Dropped(override val bot: Bot, val cause: Throwable?) : BotOfflineEvent(), Packet, BotPassiveEvent
}

/**
 * [Bot] 主动重新登录.
 */
data class BotReloginEvent(
    override val bot: Bot,
    val cause: Throwable?
) : BotEvent, BotActiveEvent

// endregion

// region 消息

sealed class MessageSendEvent : BotEvent, BotActiveEvent, AbstractCancellableEvent() {
    abstract val target: Contact
    final override val bot: Bot
        get() = target.bot

    data class GroupMessageSendEvent(
        override val target: Group,
        var message: MessageChain
    ) : MessageSendEvent(), CancellableEvent

    data class FriendMessageSendEvent(
        override val target: QQ,
        var message: MessageChain
    ) : MessageSendEvent(), CancellableEvent
}

/**
 * 消息撤回事件. 可是任意消息被任意人撤回.
 */
sealed class MessageRecallEvent : BotEvent {
    /**
     * 消息原发送人
     */
    abstract val authorId: Long

    /**
     * 消息 id.
     * @see MessageSource.id
     */
    abstract val messageId: Long

    /**
     * 原发送时间
     */
    abstract val messageTime: Int // seconds

    /**
     * 好友消息撤回事件, 暂不支持解析.
     */
    data class FriendRecall(
        override val bot: Bot,
        override val messageId: Long,
        override val messageTime: Int,
        /**
         * 撤回操作人, 可能为 [Bot.uin] 或好友的 [QQ.id]
         */
        val operator: Long
    ) : MessageRecallEvent(), Packet {
        override val authorId: Long
            get() = bot.uin
    }

    data class GroupRecall(
        override val bot: Bot,
        override val authorId: Long,
        override val messageId: Long,
        override val messageTime: Int,
        /**
         * 操作人. 为 null 时则为 [Bot] 操作.
         */
        override val operator: Member?,
        override val group: Group
    ) : MessageRecallEvent(), GroupOperableEvent, Packet
}

@OptIn(MiraiExperimentalAPI::class)
val MessageRecallEvent.GroupRecall.author: Member
    get() = if (authorId == bot.uin) group.botAsMember else group[authorId]

val MessageRecallEvent.FriendRecall.isByBot: Boolean get() = this.operator == bot.uin

val MessageRecallEvent.isByBot: Boolean
    get() = when (this) {
        is MessageRecallEvent.FriendRecall -> this.isByBot
        is MessageRecallEvent.GroupRecall -> (this as GroupOperableEvent).isByBot
    }

// endregion

// region 图片

/**
 * 图片上传前. 可以阻止上传
 */
data class BeforeImageUploadEvent(
    val target: Contact,
    val source: ExternalImage
) : BotEvent, BotActiveEvent, AbstractCancellableEvent() {
    override val bot: Bot
        get() = target.bot
}

/**
 * 图片上传完成
 *
 * @see Succeed
 * @see Failed
 */
sealed class ImageUploadEvent : BotEvent, BotActiveEvent, AbstractCancellableEvent() {
    abstract val target: Contact
    abstract val source: ExternalImage
    override val bot: Bot
        get() = target.bot

    data class Succeed(
        override val target: Contact,
        override val source: ExternalImage,
        val image: Image
    ) : ImageUploadEvent(), CancellableEvent

    data class Failed(
        override val target: Contact,
        override val source: ExternalImage,
        val errno: Int,
        val message: String
    ) : ImageUploadEvent(), CancellableEvent
}

// endregion

// region 群

/**
 * Bot 在群里的权限被改变. 操作人一定是群主
 */
data class BotGroupPermissionChangeEvent(
    override val group: Group,
    val origin: MemberPermission,
    val new: MemberPermission
) : BotPassiveEvent, GroupEvent, Packet

/**
 * Bot 被禁言
 */
data class BotMuteEvent(
    val durationSeconds: Int,
    /**
     * 操作人.
     */
    val operator: Member
) : GroupEvent, Packet, BotPassiveEvent {
    override val group: Group
        get() = operator.group
}

/**
 * Bot 被取消禁言
 */
data class BotUnmuteEvent(
    /**
     * 操作人.
     */
    val operator: Member
) : GroupEvent, Packet, BotPassiveEvent {
    override val group: Group
        get() = operator.group
}

/**
 * Bot 加入了一个新群
 */
@MiraiExperimentalAPI
data class BotJoinGroupEvent(
    override val group: Group
) : BotPassiveEvent, GroupEvent, Packet

// region 群设置

/**
 * 群设置改变. 此事件广播前修改就已经完成.
 */
interface GroupSettingChangeEvent<T> : GroupEvent, BotPassiveEvent, BroadcastControllable {
    val origin: T
    val new: T

    override val shouldBroadcast: Boolean
        get() = origin != new
}

/**
 * 群名改变. 此事件广播前修改就已经完成.
 */
data class GroupNameChangeEvent(
    override val origin: String,
    override val new: String,
    override val group: Group,
    val isByBot: Boolean // 无法获取操作人
) : GroupSettingChangeEvent<String>, Packet

/**
 * 入群公告改变. 此事件广播前修改就已经完成.
 */
data class GroupEntranceAnnouncementChangeEvent(
    override val origin: String,
    override val new: String,
    override val group: Group,
    /**
     * 操作人. 为 null 时则是机器人操作
     */
    override val operator: Member?
) : GroupSettingChangeEvent<String>, Packet, GroupOperableEvent


/**
 * 群 "全员禁言" 功能状态改变. 此事件广播前修改就已经完成.
 */
data class GroupMuteAllEvent(
    override val origin: Boolean,
    override val new: Boolean,
    override val group: Group,
    /**
     * 操作人. 为 null 时则是机器人操作
     */
    override val operator: Member?
) : GroupSettingChangeEvent<Boolean>, Packet, GroupOperableEvent


/**
 * 群 "匿名聊天" 功能状态改变. 此事件广播前修改就已经完成.
 */
data class GroupAllowAnonymousChatEvent(
    override val origin: Boolean,
    override val new: Boolean,
    override val group: Group,
    /**
     * 操作人. 为 null 时则是机器人操作
     */
    override val operator: Member?
) : GroupSettingChangeEvent<Boolean>, Packet, GroupOperableEvent


/**
 * 群 "坦白说" 功能状态改变. 此事件广播前修改就已经完成.
 */
data class GroupAllowConfessTalkEvent(
    override val origin: Boolean,
    override val new: Boolean,
    override val group: Group,
    val isByBot: Boolean // 无法获取操作人
) : GroupSettingChangeEvent<Boolean>, Packet

/**
 * 群 "允许群员邀请好友加群" 功能状态改变. 此事件广播前修改就已经完成.
 */
data class GroupAllowMemberInviteEvent(
    override val origin: Boolean,
    override val new: Boolean,
    override val group: Group,
    /**
     * 操作人. 为 null 时则是机器人操作
     */
    override val operator: Member?
) : GroupSettingChangeEvent<Boolean>, Packet, GroupOperableEvent


// endregion


// region 群成员

// region 成员变更

/**
 * 成员加入群的事件
 */
data class MemberJoinEvent(override val member: Member) : GroupMemberEvent, BotPassiveEvent, Packet

/**
 * 成员离开群的事件
 */
sealed class MemberLeaveEvent : GroupMemberEvent {
    /**
     * 成员被踢出群. 成员不可能是机器人自己.
     */
    data class Kick(
        override val member: Member,
        /**
         * 操作人. 为 null 则是机器人操作
         */
        override val operator: Member?
    ) : MemberLeaveEvent(), Packet, GroupOperableEvent {
        override fun toString(): String {
            return "MemberLeaveEvent.Kick(member=$member, operator=$operator)"
        }
    }

    /**
     * 成员主动离开
     */
    data class Quit(override val member: Member) : MemberLeaveEvent(), Packet {
        override fun toString(): String {
            return "MemberLeaveEvent.Quit(member=$member)"
        }
    }
}

// endregion

// region 名片和头衔

/**
 * 群名片改动. 此事件广播前修改就已经完成.
 */
data class MemberCardChangeEvent(
    /**
     * 修改前
     */
    val origin: String,

    /**
     * 修改后
     */
    val new: String,

    override val member: Member,

    /**
     * 操作人. 为 null 时则是机器人操作. 可能与 [member] 引用相同, 此时为群员自己修改.
     */
    override val operator: Member?
) : GroupMemberEvent, GroupOperableEvent

/**
 * 群头衔改动. 一定为群主操作
 */
data class MemberSpecialTitleChangeEvent(
    /**
     * 修改前
     */
    val origin: String,

    /**
     * 修改后
     */
    val new: String,

    override val member: Member,

    /**
     * 操作人.
     * 不为 null 时一定为群主. 可能与 [member] 引用相同, 此时为群员自己修改.
     * 为 null 时则是机器人操作.
     */
    override val operator: Member?
) : GroupMemberEvent, GroupOperableEvent

// endregion


// region 成员权限

/**
 * 成员权限改变的事件. 成员不可能是机器人自己.
 */
data class MemberPermissionChangeEvent(
    override val member: Member,
    val origin: MemberPermission,
    val new: MemberPermission
) : GroupMemberEvent, BotPassiveEvent, Packet

// endregion


// region 禁言

/**
 * 群成员被禁言事件. 被禁言的成员都不可能是机器人本人
 */
data class MemberMuteEvent(
    override val member: Member,
    val durationSeconds: Int,
    /**
     * 操作人. 为 null 则为机器人操作
     */
    override val operator: Member?
) : GroupMemberEvent, Packet, GroupOperableEvent

/**
 * 群成员被取消禁言事件. 被禁言的成员都不可能是机器人本人
 */
data class MemberUnmuteEvent(
    override val member: Member,
    /**
     * 操作人. 为 null 则为机器人操作
     */
    override val operator: Member?
) : GroupMemberEvent, Packet, GroupOperableEvent

// endregion

// endregion

// endregion