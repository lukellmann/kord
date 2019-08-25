package com.gitlab.kordlib.core.`object`.data

import com.gitlab.kordlib.rest.json.response.InviteResponse
import kotlinx.serialization.Serializable

@Serializable
data class InviteData(
        val code: String,
        val guildId: String?,
        val channelId: String
) {
    companion object {
        fun from(entity: InviteResponse) = with(entity) {
            InviteData(code!!, guild!!.id, channel!!.id)
        }
    }
}