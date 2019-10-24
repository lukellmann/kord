package com.gitlab.kordlib.core.entity

import com.gitlab.kordlib.common.entity.Permissions
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.behavior.RoleBehavior
import com.gitlab.kordlib.core.cache.data.RoleData
import java.awt.Color

data class Role(val data: RoleData, override val kord: Kord) : RoleBehavior {
    override val id: Snowflake
        get() = Snowflake(data.id)

    override val guildId: Snowflake
        get() = Snowflake(data.guildId)

    val color: Color get() = Color(data.color, true)

    val hoisted: Boolean get() = data.hoisted

    val managed: Boolean get() = data.managed

    val mentionable: Boolean get() = data.mentionable

    val name: String get() = data.name

    val permissions: Permissions get() = data.permissions

    val rawPosition: Int get() = data.position

    override fun compareTo(other: Entity): Int = when (other) {
        is Role -> compareBy<Role> { it.rawPosition }.thenBy { it.guildId }.compare(this, other)
        else -> super.compareTo(other)
    }

}