param(
    [Parameter(Mandatory = $true)][string] $JavaHome,
    [Parameter(Mandatory = $true)][string] $CompileArgs,
    [Parameter(Mandatory = $true)][string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
# Reuse an existing 1.21.1 SFM toolchain classpath. Do not download or change dependencies.
$compilerArguments = Get-Content -LiteralPath $CompileArgs
$classpathIndex = [Array]::IndexOf($compilerArguments, '-classpath')
if ($classpathIndex -lt 0 -or $classpathIndex + 1 -ge $compilerArguments.Length) {
    throw 'Expected an SFM javac-gametest.args file containing -classpath.'
}
$probeClasspath = $compilerArguments[$classpathIndex + 1].Trim('"')
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$probeOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path
& (Join-Path $JavaHome 'bin/javac.exe') -proc:none -cp $probeClasspath -d $probeOutput (Join-Path $PSScriptRoot 'MekanismRoundingProbe.java')
if ($LASTEXITCODE -ne 0) { throw "Probe compilation failed: $LASTEXITCODE" }
& (Join-Path $JavaHome 'bin/java.exe') -cp "$probeOutput;$probeClasspath" mekanism.common.integration.energy.forgeenergy.MekanismRoundingProbe
if ($LASTEXITCODE -ne 0) { throw "Probe failed: $LASTEXITCODE" }
