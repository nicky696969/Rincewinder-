/*
 * RINCEWINDER FORK, nový soubor (Apache 2.0, viz NOTICE-RINCEWINDER.md).
 * Pomocné funkce pro adbd z Androidu 4.1 (Sony A7 II): žádné shell_v2, žádný návratový kód, shell běží v pty.
 */
package dadb

private const val RC_MARKER = "__RC="

/** Legacy shell: "shell:CMD; echo __RC=$?"; návratový kód se vytáhne z posledního řádku, CRLF z pty se převede na LF. */
internal fun legacyShell(dadb: Dadb, command: String): AdbShellResponse {
    val raw = dadb.open("shell:$command; echo $RC_MARKER\$?").use { stream ->
        stream.source.readString(Charsets.UTF_8)
    }
    return parseLegacyShellOutput(raw)
}

internal fun parseLegacyShellOutput(raw: String): AdbShellResponse {
    val text = normalizeNewlines(raw)
    val idx = text.lastIndexOf(RC_MARKER)
    if (idx < 0) return AdbShellResponse(text, "", -1)
    val code = text.substring(idx + RC_MARKER.length).trim().toIntOrNull() ?: -1
    return AdbShellResponse(text.substring(0, idx), "", code)
}

internal fun normalizeNewlines(s: String) = s.replace("\r\n", "\n").replace('\r', '\n')

/**
 * pm install/uninstall: na 4.1 před výsledkem tiskne "\tpkg: /data/local/tmp/x.apk", takže startsWith("Success") nestačí.
 * Úspěch = některý řádek je přesně "Success".
 */
internal fun isPmSuccess(output: String): Boolean =
    normalizeNewlines(output).lineSequence().any { it.trim() == "Success" }