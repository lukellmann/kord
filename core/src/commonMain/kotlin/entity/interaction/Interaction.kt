package dev.kord.core.entity.interaction

import dev.kord.common.Locale
import dev.kord.common.entity.InteractionType
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.OptionalSnowflake
import dev.kord.common.entity.optional.mapList
import dev.kord.common.entity.optional.orEmpty
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.InteractionBehavior
import dev.kord.core.cache.data.InteractionData
import dev.kord.core.entity.User
import dev.kord.core.entity.monetization.Entitlement
import dev.kord.core.entity.monetization.Sku
import dev.kord.core.supplier.EntitySupplyStrategy

/**
 * An instance of an
 * [interaction](https://discord.com/developers/docs/interactions/receiving-and-responding#interactions).
 *
 * @see ActionInteraction
 * @see DataInteraction
 */
public sealed interface Interaction : InteractionBehavior {
    public val data: InteractionData

    override val id: Snowflake get() = data.id

    override val applicationId: Snowflake get() = data.applicationId

    override val channelId: Snowflake get() = data.channel.value?.id ?: data.channelId.value!!

    override val token: String get() = data.token

    /**
     * The type of the interaction.
     */
    public val type: InteractionType get() = data.type

    /** The invoker of the interaction. */
    public val user: User

    /**
     * The selected language of the invoking user.
     *
     * This is available on all interaction types except [InteractionType.Ping]
     */
    public val locale: Locale? get() = data.locale.value

    /**
     * The guild's preferred locale, if invoked in a guild.
     */
    public val guildLocale: Locale? get() = data.guildLocale.value

    /**
     * read-only property, always 1
     */
    public val version: Int get() = data.version

    /**
     * For [monetized apps](https://discord.com/developers/docs/monetization/overview), any [Entitlement]s for the
     * [invoking user][user], representing access to premium [Sku]s.
     */
    public val entitlements: List<Entitlement> get() = data.entitlements.mapList { Entitlement(it, kord) }.orEmpty()

    override fun withStrategy(strategy: EntitySupplyStrategy<*>): Interaction

    public companion object {
        public fun from(
            data: InteractionData,
            kord: Kord,
            strategy: EntitySupplyStrategy<*> = kord.resources.defaultStrategy
        ): Interaction = when (val type = data.type) {
            InteractionType.Component -> ComponentInteraction(data, kord, strategy.supply(kord))
            InteractionType.AutoComplete -> AutoCompleteInteraction(data, kord, strategy.supply(kord))
            InteractionType.ModalSubmit -> ModalSubmitInteraction(data, kord, strategy.supply(kord))
            InteractionType.ApplicationCommand -> {
                if (data.guildId !is OptionalSnowflake.Missing) {
                    GuildApplicationCommandInteraction(data, kord, strategy.supply(kord))
                } else {
                    GlobalApplicationCommandInteraction(data, kord, strategy.supply(kord))
                }
            }
            InteractionType.Ping, is InteractionType.Unknown -> error("Unsupported interaction type: $type")
        }
    }
}
