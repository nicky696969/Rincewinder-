<#
.SYNOPSIS
  Změří Rincewinder Agent z PC přes HTTP (F0b, otázky #3, #5, #6, #7). Na těle jen čte, nic nemění.

.DESCRIPTION
  Předpoklad: Agent běží na těle (spuštěn z Application menu) a PC je ve stejné Wi-Fi.
  Kroky: /ping + latence, /status, /ls, /thumbs, /thumb (obě velikosti), stažení jednoho ARW s měřením
  MB/s a porovnáním velikosti i MD5 s lokálním Get-FileHash, /md5 (čas na těle), /probe, RINCEAGT.LOG.
  Výsledky se uloží do docs\f0b\vysledky_<čas>.txt.

.PARAMETER Ip      IP těla (výchozí poslední známá 10.98.92.25; po restartu ověř v Tweaku nebo z QR).
.PARAMETER Index   Pořadí ARW ve složce (0 = první). Vyber soubor, který nevadí číst (čtení nic nemění).
.PARAMETER Lens    Navíc /probe?lens=1 (otevře kameru; jen při vypnuté kamerové aplikaci, např. TrapFocus).
#>
param(
    [string]$Ip = "10.98.92.25",
    [int]$Port = 8808,
    [string]$Dir = "DCIM/100MSDCF",
    [int]$Index = 1,
    [switch]$Lens
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$Root = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $Root "docs\f0b"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$Stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$OutFile = Join-Path $OutDir ("vysledky_" + $Stamp + ".txt")
$Base = "http://${Ip}:${Port}"
$Tmp = Join-Path $env:TEMP ("rw_f0b_" + $Stamp)
New-Item -ItemType Directory -Force $Tmp | Out-Null

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(10)
$lines = New-Object System.Collections.Generic.List[string]
function Say([string]$s) { Write-Host $s; $lines.Add($s) }

function GetText([string]$path) {
    $client.GetStringAsync($Base + $path).GetAwaiter().GetResult()
}

function Download([string]$path, [string]$dest) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $resp = $client.GetAsync($Base + $path, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    if (-not $resp.IsSuccessStatusCode) { throw ("HTTP " + [int]$resp.StatusCode + " pro " + $path) }
    $ttfb = $sw.Elapsed.TotalMilliseconds
    $in = $resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $fs = [IO.File]::Create($dest)
    try { $in.CopyTo($fs) } finally { $fs.Close(); $in.Close() }
    $sw.Stop()
    $len = (Get-Item $dest).Length
    [pscustomobject]@{ Bytes = $len; Seconds = $sw.Elapsed.TotalSeconds; Ttfb = $ttfb; MBps = ($len / 1MB) / [Math]::Max($sw.Elapsed.TotalSeconds, 0.001);
        Headers = ($resp.Headers + $resp.Content.Headers | ForEach-Object { $_.Key + "=" + ($_.Value -join ",") }) -join "; " }
}

Say ("F0b měření " + $Stamp + "  agent " + $Base)

Say "--- #0 dosažitelnost a latence /ping (10×)"
$lat = @()
for ($i = 0; $i -lt 10; $i++) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = GetText "/ping"
    $sw.Stop()
    $lat += $sw.Elapsed.TotalMilliseconds
    if ($i -eq 0) { Say ("odpověď: " + $r.Trim()) }
}
Say ("latence ms: min {0:N0}  medián {1:N0}  max {2:N0}" -f ($lat | Measure-Object -Minimum).Minimum, ($lat | Sort-Object)[5], ($lat | Measure-Object -Maximum).Maximum)

Say "--- #7 /status"
$status = GetText "/status"
Say $status

Say "--- /ls $Dir"
$ls = GetText ("/ls?path=" + [Uri]::EscapeDataString($Dir))
$arws = @($ls -split "`n" | Where-Object { $_ -match "\.ARW;" })
Say ("souborů ARW: " + $arws.Count)
if ($arws.Count -eq 0) { Say "Žádné ARW, končím."; $lines | Set-Content $OutFile -Encoding UTF8; exit 1 }
$pick = $arws[[Math]::Min($Index, $arws.Count - 1)]
$name = ($pick -split ";")[0]
$size = [long](($pick -split ";")[1])
$rel = $Dir + "/" + $name
Say ("vybraný soubor: " + $rel + "  " + $size + " B")

Say "--- #6 vložené JPEG (/thumbs, /thumb)"
Say (GetText ("/thumbs?path=" + [Uri]::EscapeDataString($rel)))
foreach ($min in @(0, 100)) {
    $dst = Join-Path $Tmp ("thumb_min$min.jpg")
    $d = Download ("/thumb?path=" + [Uri]::EscapeDataString($rel) + "&min=" + $min) $dst
    Say ("/thumb?min={0}: {1} B za {2:N0} ms (první bajt {3:N0} ms)  {4}" -f $min, $d.Bytes, ($d.Seconds * 1000), $d.Ttfb, $d.Headers)
}
Say ("náhledy uloženy v " + $Tmp)

Say "--- #3 propustnost: stažení celého ARW"
$dst = Join-Path $Tmp $name
$d = Download ("/file?path=" + [Uri]::EscapeDataString($rel)) $dst
Say ("{0} B za {1:N1} s = {2:N2} MB/s (první bajt {3:N0} ms)" -f $d.Bytes, $d.Seconds, $d.MBps, $d.Ttfb)
Say ("velikost sedí s /ls: " + ($d.Bytes -eq $size))
$local = (Get-FileHash -LiteralPath $dst -Algorithm MD5).Hash.ToLower()
Say ("MD5 lokálně: " + $local)

Say "--- /md5 na těle (měří i rychlost čtení karty)"
$sw = [Diagnostics.Stopwatch]::StartNew()
$remote = (GetText ("/md5?path=" + [Uri]::EscapeDataString($rel))).Trim()
$sw.Stop()
Say ("MD5 z Agenta: " + $remote + "  za {0:N1} s = {1:N2} MB/s čtení karty" -f $sw.Elapsed.TotalSeconds, (($size / 1MB) / [Math]::Max($sw.Elapsed.TotalSeconds, 0.001)))
Say ("MD5 shodné: " + ($local -eq $remote))

Say "--- #5 reflexe Sony API (/probe)"
Say (GetText "/probe")
if ($Lens) {
    Say "--- /probe?lens=1 (otevření kamery)"
    Say (GetText "/probe?lens=1")
}

Say "--- RINCEAGT.LOG (posledních 60 řádků)"
try {
    $logp = Join-Path $Tmp "RINCEAGT.LOG"
    $null = Download "/file?path=RINCEAGT.LOG" $logp
    Get-Content $logp -Tail 60 -Encoding UTF8 | ForEach-Object { Say $_ }
} catch { Say ("log nejde stáhnout: " + $_) }

$lines | Set-Content $OutFile -Encoding UTF8
Write-Host ""
Write-Host ("Výsledky: " + $OutFile)
