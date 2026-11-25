package com.example

import android.app.Application
import com.example.data.repo.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AmmarApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val chatRepository: ChatRepository by lazy {
        ChatRepository(this, applicationScope)
    }
}
