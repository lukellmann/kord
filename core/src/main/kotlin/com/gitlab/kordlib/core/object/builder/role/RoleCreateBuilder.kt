package com.gitlab.kordlib.core.`object`.builder.role

import com.gitlab.kordlib.common.entity.Permissions
import com.gitlab.kordlib.common.entity.Role
import com.gitlab.kordlib.core.`object`.builder.AuditRequestBuilder
import com.gitlab.kordlib.core.`object`.builder.RequestBuilder
import com.gitlab.kordlib.rest.json.request.GuildRoleCreateRequest
import java.awt.Color

class RoleCreateBuilder : AuditRequestBuilder<GuildRoleCreateRequest>{
    override var reason: String? = null
    var color: Color? = null
    var hoist: Boolean = false
    var name: String? = null
    var mentionable: Boolean = false
    var permissions: Permissions? = null

    override fun toRequest(): GuildRoleCreateRequest = GuildRoleCreateRequest(
            color = color?.rgb ?: 0,
            separate = hoist,
            name = name,
            mentionable = mentionable,
            permissions = permissions
    )
}