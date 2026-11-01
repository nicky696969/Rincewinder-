<#
.SYNOPSIS
  Otestuje jádro Rincewinder Agenta na PC (bez těla).

.DESCRIPTION
  1) Kontrola API: celý zdroj (jádro i Android vrstva) se přeloží proti android-10 android.jar jako
     bootclasspath. Cokoli novějšího než API 10 (např. java.nio.file, StandardCharsets) tu spadne,
     takže se to nedostane na tělo s Androidem 4.1.
  2) Test jádra: QR proti dekodéru ZXing (z Android Studia), HTTP server (Range, md5, traversal),
     parser vložených JPEG proti skutečnému ARW.

.PARAMETER ArwDir
  Složka se vzorovými ARW (jen čtení). Výchozí D:\TrapFocus_test\2026-10-27.
#>
param(
    [string]$ArwDir = "D:\TrapFocus_test\2026-10-27"
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Agent = Join-Path $Root "agent"
$Out = Join-Path $Root "build\test"

$Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$AndroidJar = Join-Path $Sdk "platforms\android-10\android.jar"
$Jdk = @($env:JAVA_HOME, (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\javac.exe")) } | Select-Object -First 1
if (-not $Jdk) { throw "JDK nenalezen" }
$Javac = Join-Path $Jdk "bin\javac.exe"
$Java = Join-Path $Jdk "bin\java.exe"
$Zxing = Get-ChildItem "C:\Program Files\Android\Android Studio\plugins\android\lib" -Filter "core-3*.jar" | Select-Object -First 1
if (-not $Zxing) { throw "ZXing core.jar nenalezen v Android Studiu" }

function Run([string]$exe, [string[]]$a) {
    & $exe @a
    if ($LASTEXITCODE -ne 0) { throw ("Selhal: " + $exe + " (kód " + $LASTEXITCODE + ")") }
}

if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Force "$Out\apicheck", "$Out\core", "$Out\test" | Out-Null

Write-Host "=== 1) kontrola API 10 (celý zdroj proti android.jar)"
$all = @(Get-ChildItem (Join-Path $Agent "core"), (Join-Path $Agent "app\src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$gen = Join-Path $Root "build\gen"
if (Test-Path $gen) { $all += @(Get-ChildItem $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName }) }
Run $Javac (@("-source", "8", "-target", "8", "-Xlint:-options", "-nowarn", "-bootclasspath", $AndroidJar, "-d", "$Out\apicheck") + $all)
Write-Host "OK: žádné API nad úrovní 10"

Write-Host "=== 2) test jádra na PC"
$core = @(Get-ChildItem (Join-Path $Agent "core") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Run $Javac (@("--release", "8", "-nowarn", "-d", "$Out\core") + $core)
Run $Javac @("-nowarn", "-cp", ("$Out\core;" + $Zxing.FullName), "-d", "$Out\test", (Join-Path $Agent "tests\CoreSelfTest.java"))
Run $Java @("-cp", ("$Out\core;$Out\test;" + $Zxing.FullName), "CoreSelfTest", $ArwDir)
