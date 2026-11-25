package com.example.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.example.MainActivity
import com.example.network.HotspotController
import com.example.network.HotspotNodeRuntime
import com.example.network.HotspotNodeState
import com.example.network.HotspotRelayService
import kotlinx.coroutines.flow.StateFlow

class HotspotNodeViewModel(application: Application) : AndroidViewModel(application) {
    private val hotspotController = HotspotController(application)
    val nodeState: StateFlow<HotspotNodeState> = HotspotNodeRuntime.state

    fun activateHotspotNode() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, HotspotRelayService::class.java).apply {
            action = HotspotRelayService.ACTION_START_NODE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun deactivateHotspotNode() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, HotspotRelayService::class.java).apply {
            action = HotspotRelayService.ACTION_STOP_NODE
        }
        ctx.startService(intent)
    }

    fun openSystemHotspotSettings() {
        hotspotController.openSystemHotspotSettings()
    }

    fun openMainChatApp() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
