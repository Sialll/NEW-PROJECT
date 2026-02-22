package com.example.moneymind.notification

import com.example.moneymind.BuildConfig
import com.example.moneymind.core.ServiceLocator
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "MM.NotificationListener"

class BankNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentNotificationCache = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 250
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val sourcePackage = sbn.packageName?.trim().orEmpty()
        if (sourcePackage.isBlank()) return
        if (sourcePackage == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        if (!NotificationSourcePolicy.isSupported(applicationContext, sourcePackage, title, text)) {
            logDebug("Notification filtered: package=$sourcePackage")
            return
        }
        if (!shouldIngest(sourcePackage, sbn.postTime, title, text)) {
            logDebug("Notification deduped: package=$sourcePackage")
            return
        }

        val parsed = NotificationMessageParser.parse(title, text) ?: run {
            logDebug("Notification parse failed: package=$sourcePackage")
            return
        }

        logDebug("Notification ingested: package=$sourcePackage amount=${parsed.signedAmount}")
        serviceScope.launch {
            ServiceLocator.repository(applicationContext).ingestNotification(parsed)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun shouldIngest(
        sourcePackage: String,
        postTime: Long,
        title: String?,
        text: String?
    ): Boolean {
        val identity = buildString {
            append(sourcePackage)
            append('|')
            append(title?.trim().orEmpty())
            append('|')
            append(text?.trim().orEmpty())
        }

        val now = System.currentTimeMillis()
        val minEventTime = if (postTime > 0L) postTime else now
        val duplicateWindowMs = 12_000L

        synchronized(recentNotificationCache) {
            val iterator = recentNotificationCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > 120_000L) iterator.remove()
            }
            val lastSeenAt = recentNotificationCache[identity]
            if (lastSeenAt != null && (minEventTime - lastSeenAt).absoluteValue <= duplicateWindowMs) {
                return false
            }
            recentNotificationCache[identity] = minEventTime
            return true
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    companion object {
        fun requestRebindSafely(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, BankNotificationListener::class.java))
            }
        }
    }
}