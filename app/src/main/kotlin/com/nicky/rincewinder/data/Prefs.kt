package com.nicky.rincewinder.data

import android.content.Context
import androidx.core.content.edit

/** Profil těla a nastavení (UX_NAVRH „Profily těla“): mac, poslední IP, klíč z QR, poslední rychlost, složka SAF. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("rincewinder", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit { putBoolean("onboarded", v) }

    /** Strom SAF (`Pictures/Rincewinder`), null = nevybráno. */
    var treeUri: String?
        get() = p.getString("treeUri", null)
        set(v) = p.edit { putString("treeUri", v) }

    var bodyMac: String?
        get() = p.getString("bodyMac", null)
        set(v) = p.edit { putString("bodyMac", v) }

    var lastIp: String?
        get() = p.getString("lastIp", null)
        set(v) = p.edit { putString("lastIp", v) }

    /** Klíč z QR (`&k=`), nutný pro mazání na Agentu se `secured: true`. */
    var agentKey: String?
        get() = p.getString("agentKey", null)
        set(v) = p.edit { putString("agentKey", v) }

    /** Poslední naměřená rychlost pro ETA na Domů; 0 = neměřeno. */
    var lastMbps: Float
        get() = p.getFloat("lastMbps", 0f)
        set(v) = p.edit { putFloat("lastMbps", v) }

    var lastTransferSummary: String?
        get() = p.getString("lastTransfer", null)
        set(v) = p.edit { putString("lastTransfer", v) }

    /** První spárování proběhlo (volba „od dneška / vše / nic“ už byla položena). */
    var firstPairingDone: Boolean
        get() = p.getBoolean("firstPairingDone", false)
        set(v) = p.edit { putBoolean("firstPairingDone", v) }
}
