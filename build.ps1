#requires -Version 5.1
<#
.SYNOPSIS
    Builds the FastScript plugin jar from source.

.DESCRIPTION
    The build is deliberately self-contained: no Gradle or Maven is required, and every
    third-party jar is resolved into .\libs by a small bundled resolver (tools\Fetch.java).

    Steps:
      1. compile the build tools and resolve dependencies into libs\
      2. compile the plugin against the Paper API and ASM
      3. package build\FastScript-<version>.jar with ASM bundled under a private namespace

    ASM is bundled because the engine emits bytecode at run time. Its classes are relocated to
    io.github.dsh.fastscript.lib.asm, so a different ASM version loaded by the server or by
    another plugin can never interfere with script compilation.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\build.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\build.ps1 -SkipDependencies
#>
[CmdletBinding()]
param(
    [string]$PaperVersion = '1.21.8-R0.1-SNAPSHOT',
    [string]$AsmVersion = '9.8',
    [string]$RelocateFrom = 'org.objectweb.asm',
    [string]$RelocateTo = 'io.github.dsh.fastscript.lib.asm',
    [string]$OutputName = 'FastScript-1.0.0.jar',
    [switch]$SkipDependencies
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$libs = Join-Path $root 'libs'
$build = Join-Path $root 'build'
$toolClasses = Join-Path $build 'tools'
$classes = Join-Path $build 'classes'
$outJar = Join-Path $build $OutputName

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { (Get-Command java).Source | Split-Path -Parent }
$javac = Join-Path $javaHome 'bin\javac.exe'
$java = Join-Path $javaHome 'bin\java.exe'
foreach ($tool in @($javac, $java)) {
    if (-not (Test-Path $tool)) { throw "Java toolchain not found: $tool" }
}

function Step([string]$message) {
    Write-Host ''
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Invoke-Javac {
    param([string]$ClassPath, [string[]]$Sources, [string]$Destination, [string[]]$ExtraArgs = @())
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    $arguments = @('-encoding', 'UTF-8', '--release', '21') +
        ($(if ($ClassPath) { @('-cp', $ClassPath) } else { @() })) +
        @('-d', $Destination) + $ExtraArgs + $Sources
    & $javac @arguments
    if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
}

# ---------------------------------------------------------------------------- 1. tools and dependencies

Step 'Compiling build tools'
New-Item -ItemType Directory -Force -Path $toolClasses, $libs | Out-Null

if (-not $SkipDependencies) {
    Step 'Resolving dependencies into libs\'
    Invoke-Javac -ClassPath '' -Sources @(Join-Path $root 'tools\Fetch.java') -Destination $toolClasses
    & $java -cp $toolClasses Fetch $libs `
        'https://repo.papermc.io/repository/maven-public/,https://repo1.maven.org/maven2/' `
        "io.papermc.paper:paper-api:$PaperVersion" `
        "org.ow2.asm:asm:$AsmVersion" `
        "org.ow2.asm:asm-tree:$AsmVersion" `
        "org.ow2.asm:asm-commons:$AsmVersion"
    if ($LASTEXITCODE -ne 0) { throw 'dependency resolution failed' }
}

$allJars = @(Get-ChildItem $libs -Filter *.jar | ForEach-Object { $_.FullName })
$asmJars = @(Get-ChildItem $libs -Filter 'asm-*.jar' | ForEach-Object { $_.FullName })
if ($allJars.Count -eq 0) { throw 'no dependencies found in libs\; run without -SkipDependencies' }
if ($asmJars.Count -eq 0) { throw 'ASM jars are missing from libs\; run without -SkipDependencies' }
# Paper API jars are compile-only: the server provides them at run time. Only ASM is bundled
# with the plugin, so anything else may sit on the compile class path.
$pluginClassPath = @($allJars | Where-Object { (Split-Path $_ -Leaf) -notmatch '^asm' })

# ---------------------------------------------------------------------------- 2. compile plugin

Step 'Compiling the plugin sources'
Remove-Item $classes -Recurse -Force -ErrorAction SilentlyContinue
$pluginSources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java |
        ForEach-Object { $_.FullName })
if ($pluginSources.Count -eq 0) { throw 'no plugin sources found' }
Invoke-Javac -ClassPath (($pluginClassPath + $asmJars) -join ';') -Sources $pluginSources `
    -Destination $classes -ExtraArgs @('-Xlint:all,-serial,-deprecation,-this-escape')

Copy-Item (Join-Path $root 'src\main\resources\plugin.yml') $classes -Force

# ---------------------------------------------------------------------------- 3. package jar

Step "Packaging $OutputName with ASM relocated to $RelocateTo"
Invoke-Javac -ClassPath ($asmJars -join ';') -Sources @(Join-Path $root 'tools\Package.java') `
    -Destination $toolClasses
# The plugin classes reference org.objectweb.asm, so the ASM jars are inputs too: the packager
# rewrites those references and emits the shaded classes under the private namespace.
& $java -cp (($asmJars + $toolClasses) -join ';') Package $outJar $RelocateFrom $RelocateTo `
    @asmJars $classes
if ($LASTEXITCODE -ne 0) { throw 'packaging failed' }

$size = [math]::Round((Get-Item $outJar).Length / 1KB, 1)
Write-Host ''
Write-Host "Built $outJar ($size KB)" -ForegroundColor Green
Write-Host "Copy it into your server's plugins\ directory and restart the server."
