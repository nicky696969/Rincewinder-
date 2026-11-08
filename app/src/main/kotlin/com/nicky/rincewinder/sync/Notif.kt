package com.nicky.rincewinder.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.nicky.rincewinder.ui.MainActivity

/** Notifikace přenosu (UX_WIREFRAMES §5): trvalá s postupem a akcemi, výpadek s vibrací, dokončení s haptikou. */
object Notif {
    const val CH_TRANSFER = "transfer"
    const val CH_EVENTS = "events"
    const val ID_TRANSFER = 1
    const val ID_EVENT = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRANSFER, "Přenos z těla", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Postup stahování RAWů"
        })
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "Události přenosu", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Výpadek těla, dokončení"
            enableVibration(true)
        })
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(ctx: Context, act: String, req: Int): PendingIntent = PendingIntent.getService(
        ctx, req, Intent(ctx, SyncService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(ctx: Context, title: String, text: String, done: Int, total: Int, pausedByBody: Boolean): Notification =
        NotificationCompat.Builder(ctx, CH_TRANSFER)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done, total == 0)
            .setContentIntent(openApp(ctx))
            .addAction(0, if (pausedByBody) "Zkusit znovu" else "Pozastavit", action(ctx, SyncService.ACT_PAUSE, 1))
            .addAction(0, "Zrušit", action(ctx, SyncService.ACT_CANCEL, 2))
            .build()

    fun event(ctx: Context, title: String, text: String, vibrate: Boolean) {
        val n = NotificationCompat.Builder(ctx, CH_EVENTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_EVENT, n)
        if (vibrate) runCatching {
            ctx.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun cancelEvent(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID_EVENT)
}
