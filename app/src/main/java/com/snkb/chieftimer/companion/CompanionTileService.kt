package com.snkb.chieftimer.companion

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile to toggle the floating Clash Companion import button on demand. */
class CompanionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        if (CompanionService.isShowingBar()) {
            CompanionService.hide(this)
            setTileState(false)
            return
        }
        if (!CompanionPrefs.overlayAllowed(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(
                    this,
                    9,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                runCatching { startActivityAndCollapse(pi) }
            } else {
                @Suppress("DEPRECATION")
                runCatching { startActivityAndCollapse(intent) }
            }
            return
        }
        CompanionService.activateManual(this, immediate = true)
        setTileState(true)
    }

    private fun syncTileState() {
        setTileState(CompanionService.isShowingBar())
    }

    private fun setTileState(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, CompanionTileService::class.java),
                )
            }
        }
    }
}
