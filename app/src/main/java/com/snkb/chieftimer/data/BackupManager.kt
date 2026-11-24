package com.snkb.chieftimer.data

import com.snkb.chieftimer.core.AccountMeta
import com.snkb.chieftimer.core.AccountSettings
import com.snkb.chieftimer.core.AppState
import com.snkb.chieftimer.core.GlobalSettings
import com.snkb.chieftimer.core.VillageHistory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupAccount(
    val meta: AccountMeta,
    val state: AppState,
    val settings: AccountSettings,
    val history: VillageHistory = VillageHistory(),
)

@Serializable
data class BackupBundle(
    val version: Int = 1,
    val createdAt: Long,
    val selectedTag: String?,
    val globalSettings: GlobalSettings,
    val accounts: List<BackupAccount>,
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
        fun decode(text: String): BackupBundle? = runCatching {
            val b = JSON.decodeFromString(serializer(), text)
            b.takeIf { it.version >= 1 && it.accounts.isNotEmpty() }
        }.getOrNull()
    }
}
