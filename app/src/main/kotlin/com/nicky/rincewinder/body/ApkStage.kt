package com.nicky.rincewinder.body

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nicky.rincewinder.core.PmParse
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** APK připravené k instalaci do těla: soubor v cache Pixelu + co o něm Android zjistí bez instalace. */
data class StagedApk(
    val file: File,
    val label: String,
    val pkg: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val source: String,
) {
    /** Tělo má Android 4.1.2 (SDK 16); APK s vyšším minSdk `pm` odmítne. */
    val tooNew: Boolean get() = (minSdk ?: 0) > BODY_SDK

    companion object { const val BODY_SDK = 16 }
}

data class GhAsset(val name: String, val url: String, val size: Long)
data class GhRelease(val repo: String, val tag: String, val title: String, val published: String, val prerelease: Boolean, val assets: List<GhAsset>)

/** Zdroje APK na Pixelu: soubor / Sdílet (content URI), odkaz https, GitHub Releases veřejných rep. */
object ApkStage {
    /**
     * Katalog rep s PMCA aplikacemi (repo → popis), jen nabídka „přidat“. Zdroj: `apps.yaml` v
     * ma1co/OpenMemories-AppList (seznam obchodu OpenMemories), ověřeno 14. 11. 2026; všech devět rep má
     * vydání s `.apk`. STG Uploader a FTP Uploader ze seznamu chybí: nevydávají přes GitHub.
     * Kompatibilita s A7 II u jednotlivých aplikací ověřená není.
     */
    val CATALOG: List<Pair<String, String>> = listOf(
        "ma1co/OpenMemories-Tweak" to "nastavení těla, ADB, telnet, Wi‑Fi",
        "ma1co/OpenMemories-AppStore" to "instalace aplikací přímo v těle",
        "jonasjuffinger/TimeLapse" to "intervalové snímání",
        "obs1dium/FocusBracket" to "focus bracketing",
        "obs1dium/BetterManual" to "rozšířený manuální režim (psáno pro α5100)",
        "Bostwickenator/dof-math" to "hloubka ostrosti a hyperfokální vzdálenost",
        "schnatterer/pmcaFilesystemServer" to "souborový systém těla přes HTTP",
        "LubikR/SynologyUploader" to "nahrávání snímků na Synology NAS",
        "ma1co/PMCADemo" to "ukázková aplikace frameworku",
    )

    private const val MAX_BYTES = 200L * 1024 * 1024

    private fun dir(ctx: Context) = File(ctx.cacheDir, "apk").apply { mkdirs() }

    /** Jedinečný soubor v cache: stejně pojmenovaná APK se navzájem nepřepíšou ani nesmažou. */
    private fun temp(ctx: Context, name: String): File = File.createTempFile("st_", "_" + PmParse.safeApkName(name), dir(ctx))

    /** Přečte balíček, verzi a minSdk z APK na disku; soubor, který není APK, vyhodí IOException a smaže se. */
    fun inspect(ctx: Context, file: File, source: String): StagedApk {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(file.path, 0)
        val app = info?.applicationInfo
        if (info == null || app == null) { file.delete(); throw IOException("${file.name} není platné APK") }
        app.sourceDir = file.path
        app.publicSourceDir = file.path
        val label = runCatching { app.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName
        return StagedApk(file, label, info.packageName, info.versionName, info.longVersionCode, app.minSdkVersion, source)
    }

    /** Zkopíruje dokument (SAF, Sdílet, Otevřít v) do cache. */
    fun fromUri(ctx: Context, uri: Uri): StagedApk {
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "app.apk"
        val dst = temp(ctx, name)
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("soubor nejde otevřít")
            input.use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        } catch (e: Exception) {
            dst.delete(); throw e
        }
        return inspect(ctx, dst, "soubor")
    }

    /** Stáhne APK z odkazu https (přesměrování GitHubu se následuje). [onProgress] dostává 0..1, nebo -1 bez známé délky. */
    fun fromUrl(ctx: Context, url: String, onProgress: (Float) -> Unit = {}): StagedApk {
        val u = URL(url.trim())
        if (u.protocol != "https") throw IOException("jen odkazy https")
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Rincewinder")
        c.setRequestProperty("Accept", "application/octet-stream")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code při stahování")
            val total = c.contentLengthLong
            if (total > MAX_BYTES) throw IOException("soubor je větší než 200 MB")
            // název z původního odkazu: přílohy GitHubu se přesměrují na adresu s UUID
            val dst = temp(ctx, u.path)
            var done = 0L
            try {
                c.inputStream.use { i ->
                    dst.outputStream().use { o ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = i.read(buf)
                            if (n <= 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (done > MAX_BYTES) throw IOException("soubor je větší než 200 MB")
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            } catch (e: Exception) {
                dst.delete(); throw e
            }
            return inspect(ctx, dst, u.host)
        } finally {
            c.disconnect()
        }
    }

    /** Posledních 10 vydání veřejného repa, jen s přílohami `.apk`. Bez přihlášení (limit GitHubu 60 dotazů/h). */
    fun releases(repo: String): List<GhRelease> {
        val c = URL("https://api.github.com/repos/$repo/releases?per_page=10").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Rincewinder")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (val code = c.responseCode) {
                in 200..299 -> Unit
                404 -> throw IOException("repo $repo neexistuje nebo je soukromé")
                403, 429 -> throw IOException("GitHub omezil počet dotazů, zkus to za chvíli")
                else -> throw IOException("GitHub HTTP $code")
            }
            val arr = JSONArray(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { r ->
                val assets = r.optJSONArray("assets") ?: JSONArray()
                val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    .map { GhAsset(it.optString("name"), it.optString("browser_download_url"), it.optLong("size")) }
                if (apks.isEmpty()) null
                else GhRelease(repo, r.optString("tag_name"),
                    (if (r.isNull("name")) "" else r.optString("name")).ifBlank { r.optString("tag_name") },
                    r.optString("published_at").take(10), r.optBoolean("prerelease"), apks)
            }
        } finally {
            c.disconnect()
        }
    }

    /** Smaže dočasné APK starší než den (volá se při startu). */
    fun cleanup(ctx: Context) {
        val limit = System.currentTimeMillis() - 24 * 3600_000L
        dir(ctx).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }
}
