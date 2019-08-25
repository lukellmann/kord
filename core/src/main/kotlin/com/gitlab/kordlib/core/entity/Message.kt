package com.gitlab.kordlib.core.entity

import com.gitlab.kordlib.common.entity.MessageType
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.`object`.data.MessageData
import com.gitlab.kordlib.core.`object`.data.ReactionData
import com.gitlab.kordlib.core.`object`.data.UserData
import com.gitlab.kordlib.core.behavior.MessageBehavior
import com.gitlab.kordlib.core.entity.channel.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.format.DateTimeFormatter

@ExperimentalCoroutinesApi
class Message(private val data: MessageData, override val kord: Kord) : MessageBehavior {

    override val id: Snowflake
        get() = Snowflake(data.id)

    override val channelId: Snowflake
        get() = Snowflake(data.channelId)

    val attachments: Set<Attachment> get() = data.attachments.asSequence().map { Attachment(it, kord) }.toSet()

    val author: User? get() = data.author?.let { User(UserData.from(it), kord) }

    val content: String get() = data.content

    val editedTimestamp: Instant?
        get() = data.editedTimestamp?.let {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(it, Instant::from)
        }

    val embeds: List<Embed> get() = data.embeds.map { Embed(it, kord) }

    val mentionsEveryone: Boolean get() = data.mentionEveryone

    val mentionedRoles: Set<Snowflake> get() = data.mentionRoles.map { Snowflake(it) }.toSet()

    val mentionedUsers: Set<Snowflake> get() = data.mentions.map { Snowflake(it) }.toSet()

    val reactions: Set<Reaction> get() {
        val reactions = data.reactions ?: return emptySet()
        return reactions.map { ReactionData.from(it) }.map { Reaction(it, kord) }.toSet()
    }

    val timestamp: Instant get() = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(data.timestamp, Instant::from)

    val tts: Boolean get() = data.tts

    val type: MessageType get() = data.type

    val webhookId: Snowflake? get() = data.webhookId?.let(::Snowflake)

    override suspend fun asMessage(): Message = this

    suspend fun getChannel(): Channel = kord.getChannel(channelId)!!

    suspend fun getAuthorAsMember(): Member? = data.guildId?.let { author?.asMember(Snowflake(it)) }

}
