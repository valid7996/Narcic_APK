package com.narcic.ng.fmt

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.V2rayNShareItem
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.util.JsonUtil
import com.narcic.ng.util.LogUtil
import com.narcic.ng.util.Utils

object V2rayNFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        try {
            val jsonBase64Payload = str.substringAfterLast('/')
            val jsonPayload = Utils.decode(jsonBase64Payload)
            val v2rayNShareItem = JsonUtil.fromJson(jsonPayload, V2rayNShareItem::class.java)
            return v2rayNShareItem?.toProfileItem()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse V2rayN format", e)
        }
        return null
    }
}