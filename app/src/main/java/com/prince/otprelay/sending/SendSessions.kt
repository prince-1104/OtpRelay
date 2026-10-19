package com.prince.otprelay.sending

import android.content.Context
import com.prince.otprelay.review.CandidateSessions
import kotlinx.coroutines.*

/** In-memory result tracking. Contains no code, body, or recipient and never initiates an SMS. */
object SendSessions {
    val controller = SendController()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun awaitResult(context: Context, token: String, attempt: String) {
        val app = context.applicationContext
        scope.launch {
            delay(60_000)
            if (controller.timeout(token, attempt)) CandidateSessions.discard(app, token)
        }
    }

    fun result(context: Context, token: String, attempt: String, failure: SendFailure?) {
        if (controller.result(token, attempt, failure) && failure == null)
            CandidateSessions.discard(context.applicationContext, token)
    }
}
