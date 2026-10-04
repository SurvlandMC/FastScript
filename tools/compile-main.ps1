# Compiles the FastScript main sources against the jars in libs/.
# Used by build.ps1 and by the compile-check loop during development.
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { (Get-Command java).Source | Split-Path -Parent }
$javac = Join-Path $jdk 'bin\javac.exe'
$sourceRoot = Join-Path $Root 'src\main\java'
$outDir = Join-Path $Root 'build\classes'
$libsDir = Join-Path $Root 'libs'

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# Same classpath convention as build.ps1: everything from libs\, ASM included
# (it is relocated only at packaging time, not at compile time).
$classpath = @(Get-ChildItem $libsDir -Filter *.jar | ForEach-Object { $_.FullName })
$classpath += $outDir

$sources = Get-ChildItem (Join-Path $sourceRoot '*.java') -Recurse | ForEach-Object { $_.FullName }
if ($sources.Count -eq 0) { throw "no sources found under $sourceRoot" }

$argFile = Join-Path $Root 'build\javac-main.args'
$lines = @()
$lines += '-d'
$lines += '"' + $outDir + '"'
$lines += '-encoding'
$lines += 'UTF-8'
$lines += '-Xlint:all,-serial,-this-escape'
$lines += '-Werror'
$lines += '--release'
$lines += '21'
$lines += '-cp'
$lines += '"' + ($classpath -join ';') + '"'
$lines += ($sources | ForEach-Object { '"' + $_ + '"' })
Set-Content -Path $argFile -Value $lines -Encoding UTF8

& $javac "@$argFile"
exit $LASTEXITCODE
