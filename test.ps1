#requires -Version 5.1
<#
.SYNOPSIS
    Compiles and runs the FastScript dependency-free test suite.

.DESCRIPTION
    The suite lives in src\test\java and uses a minimal built-in assertion
    framework (see io.github.dsh.fastscript.tests.Suite): no JUnit, no Maven,
    no Gradle — just javac and java like the rest of this project.

    Steps:
      1. compile the plugin sources into build\classes
      2. compile the test sources into build\test-classes
      3. run io.github.dsh.fastscript.tests.AllTests (exit code 1 on failure)

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\test.ps1
#>

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$libs = Join-Path $root 'libs'
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'

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

$allJars = @(Get-ChildItem $libs -Filter *.jar | ForEach-Object { $_.FullName })
if ($allJars.Count -eq 0) { throw 'no dependencies found in libs\; run build.ps1 first' }
$classPath = ($allJars -join ';')

Step 'Compiling the plugin sources'
Remove-Item $classes -Recurse -Force -ErrorAction SilentlyContinue
$pluginSources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java |
        ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 --release 21 -cp $classPath -d $classes @pluginSources
if ($LASTEXITCODE -ne 0) { throw 'plugin compilation failed' }

Step 'Compiling the test sources'
Remove-Item $testClasses -Recurse -Force -ErrorAction SilentlyContinue
$testSources = @(Get-ChildItem (Join-Path $root 'src\test\java') -Recurse -Filter *.java |
        ForEach-Object { $_.FullName })
if ($testSources.Count -eq 0) { throw 'no test sources found' }
& $javac -encoding UTF-8 --release 21 -cp ($classes + ';' + $classPath) -d $testClasses @testSources
if ($LASTEXITCODE -ne 0) { throw 'test compilation failed' }

Step 'Running the test suite'
& $java -cp ($classes + ';' + $testClasses + ';' + $classPath) io.github.dsh.fastscript.tests.AllTests
exit $LASTEXITCODE
