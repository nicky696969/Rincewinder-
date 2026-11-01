<#
.SYNOPSIS
  Sestaví a podepíše Rincewinder Agent APK pro Sony A7 II (Android 4.1.2 = API 16).

.DESCRIPTION
  Postup je převzatý z tools\build.ps1 projektu TrapFocus (aapt -> javac -> d8 -> aapt -> zipalign -> apksigner),
  bez nativní knihovny a bez Sony stubů (Sony API se volá reflexí).

  Podpisový klíč je NOVÝ, jen pro Rincewinder (rozhodnutí v CLAUDE.md). Úložiště a heslo jsou v .signing\
  (v .gitignore). Heslo se nikdy nepíše na příkazovou řádku: proměnná prostředí RW_KEYPASS, nebo soubor
  .signing\keypass.txt. Když klíč neexistuje, vytvoří se s náhodným heslem.
  ZÁLOHUJ .signing\ na zaheslované médium: bez klíče nepůjde aplikaci na těle aktualizovat, jen odinstalovat.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\build_agent.ps1
#>
param(
    [string]$KeyStore,
    [string]$KeyAlias = "rincewinder-agent"
)

$ErrorActionPreference = "Stop"

$Root    = Split-Path -Parent $PSScriptRoot
$Agent   = Join-Path $Root "agent"
$App     = Join-Path $Agent "app"
$Build   = Join-Path $Root "build\agent"
$Dist    = Join-Path $Root "dist"
$SignDir = Join-Path $Root ".signing"
$OrigKeyPass = $env:RW_KEYPASS

function Step([string]$text) { Write-Host ("=== " + $text) }
function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw ("Selhal příkaz: " + $exe + " (kód " + $LASTEXITCODE + ")") }
}

try {
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk")) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    if (-not $sdkCandidates) { throw "Android SDK nenalezen. Nastav ANDROID_HOME." }
    $Sdk = @($sdkCandidates)[0]
    $AndroidJar = Join-Path $Sdk "platforms\android-10\android.jar"
    if (-not (Test-Path -LiteralPath $AndroidJar)) { throw "Chybí platforma android-10: $AndroidJar" }

    $BuildTools = Get-ChildItem (Join-Path $Sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "aapt.exe") } |
        Sort-Object { [version]($_.Name -replace '[^\d\.].*$', '') } -Descending | Select-Object -First 1
    if (-not $BuildTools) { throw "V SDK není žádné build-tools s aapt.exe." }
    $BT = $BuildTools.FullName

    $Jdk = @($env:JAVA_HOME, (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")) |
        Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\javac.exe")) } | Select-Object -First 1
    if (-not $Jdk) { throw "JDK nenalezen. Nastav JAVA_HOME." }
    $env:JAVA_HOME = $Jdk
    $Javac = Join-Path $Jdk "bin\javac.exe"
    $JarExe = Join-Path $Jdk "bin\jar.exe"
    $Keytool = Join-Path $Jdk "bin\keytool.exe"

    $manifest = Get-Content -Raw -LiteralPath (Join-Path $App "AndroidManifest.xml")
    $versionName = [regex]::Match($manifest, 'android:versionName="([^"]+)"').Groups[1].Value
    if (-not $versionName) { throw "V AndroidManifest.xml chybí versionName." }
    Write-Host ("build-tools: " + $BuildTools.Name + "   JDK: " + $Jdk + "   verze: " + $versionName)

    if (Test-Path -LiteralPath $Build) { Remove-Item -LiteralPath $Build -Recurse -Force }
    foreach ($d in @("gen", "classes", "dex")) { New-Item -ItemType Directory -Force (Join-Path $Build $d) | Out-Null }
    New-Item -ItemType Directory -Force $Dist | Out-Null

    Step "aapt -> R.java"
    Run (Join-Path $BT "aapt.exe") @("package", "-f", "-m", "-J", (Join-Path $Build "gen"),
        "-M", (Join-Path $App "AndroidManifest.xml"), "-S", (Join-Path $App "res"), "-I", $AndroidJar)

    Step "kontrola API 10 (zdroj proti android.jar jako bootclasspath)"
    $srcs = @()
    $srcs += @(Get-ChildItem (Join-Path $Agent "core") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    $srcs += @(Get-ChildItem (Join-Path $App "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    $srcs += @(Get-ChildItem (Join-Path $Build "gen") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    New-Item -ItemType Directory -Force (Join-Path $Build "apicheck") | Out-Null
    Run $Javac (@("-source", "8", "-target", "8", "-Xlint:-options", "-nowarn", "-bootclasspath", $AndroidJar,
        "-d", (Join-Path $Build "apicheck")) + $srcs)

    Step "javac: aplikace"
    Run $Javac (@("--release", "8", "-nowarn", "-cp", $AndroidJar, "-d", (Join-Path $Build "classes")) + $srcs)

    Step "d8 -> classes.dex"
    Run $JarExe @("cf", (Join-Path $Build "app.jar"), "-C", (Join-Path $Build "classes"), ".")
    Run (Join-Path $BT "d8.bat") @("--release", "--min-api", "16", "--lib", $AndroidJar,
        "--output", (Join-Path $Build "dex"), (Join-Path $Build "app.jar"))

    Step "aapt: balíček"
    $base = Join-Path $Build "base.apk"
    Run (Join-Path $BT "aapt.exe") @("package", "-f", "-M", (Join-Path $App "AndroidManifest.xml"),
        "-S", (Join-Path $App "res"), "-I", $AndroidJar, "-F", $base)
    Push-Location (Join-Path $Build "dex")
    try { Run (Join-Path $BT "aapt.exe") @("add", $base, "classes.dex") | Out-Null } finally { Pop-Location }

    Step "zipalign"
    $aligned = Join-Path $Build "aligned.apk"
    Run (Join-Path $BT "zipalign.exe") @("-f", "4", $base, $aligned)

    New-Item -ItemType Directory -Force $SignDir | Out-Null
    if (-not $KeyStore) { $KeyStore = Join-Path $SignDir "rincewinder-agent.keystore" }
    $passFile = Join-Path $SignDir "keypass.txt"
    if (-not $env:RW_KEYPASS -and (Test-Path -LiteralPath $passFile)) {
        $env:RW_KEYPASS = (Get-Content -Raw -LiteralPath $passFile).Trim()
    }
    if (-not (Test-Path -LiteralPath $KeyStore)) {
        if (-not $env:RW_KEYPASS) {
            $bytes = New-Object byte[] 18
            [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
            $env:RW_KEYPASS = [Convert]::ToBase64String($bytes).Replace("+", "A").Replace("/", "B").Replace("=", "C")
            Set-Content -LiteralPath $passFile -Value $env:RW_KEYPASS -Encoding ascii
            Write-Host ("NOVÝ klíč. Heslo uloženo do " + $passFile + " (v .gitignore). ZÁLOHUJ .signing\ na zaheslované médium.")
        }
        Step "keytool: nový podpisový klíč"
        Run $Keytool @("-genkeypair", "-keystore", $KeyStore, "-alias", $KeyAlias,
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-storepass:env", "RW_KEYPASS", "-keypass:env", "RW_KEYPASS", "-dname", "CN=Rincewinder Agent")
    }
    elseif (-not $env:RW_KEYPASS) {
        throw "Úložiště klíčů existuje, ale chybí heslo. Nastav RW_KEYPASS."
    }

    Step "apksigner"
    $final = Join-Path $Dist ("RincewinderAgent_v" + $versionName + ".apk")
    Run (Join-Path $BT "apksigner.bat") @("sign", "--ks", $KeyStore, "--ks-key-alias", $KeyAlias,
        "--ks-pass", "env:RW_KEYPASS", "--key-pass", "env:RW_KEYPASS",
        "--min-sdk-version", "16", "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
        "--out", $final, $aligned)
    Run (Join-Path $BT "apksigner.bat") @("verify", "--min-sdk-version", "16", $final)
    Write-Host "podpis (otisk certifikátu):"
    Run (Join-Path $BT "apksigner.bat") @("verify", "--print-certs", $final)

    Step "HOTOVO"
    $item = Get-Item -LiteralPath $final
    Write-Host ("APK:    " + $item.FullName + "  (" + $item.Length + " B)")
    Write-Host ("sha256: " + (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLower())
}
finally {
    $env:RW_KEYPASS = $OrigKeyPass
}
