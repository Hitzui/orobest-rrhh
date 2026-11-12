param(
    [Parameter(Mandatory = $true)]
    [string]$SdkRoot
)
$ErrorActionPreference = 'Stop'
$repoDir = Split-Path $PSScriptRoot -Parent
$sdkPath = (Resolve-Path -LiteralPath $SdkRoot).Path
$sourceNative = Join-Path $sdkPath 'Windows\Lib\x64'
$jarPath = Join-Path $sdkPath 'Windows\Lib\Java\dpuareu.jar'
$cabPath = Join-Path $sdkPath 'RTE\x64\Data1.cab'
foreach ($requiredPath in @($jarPath, $cabPath, (Join-Path $sourceNative 'dpuareu_jni.dll'))) {
    if (!(Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "No se encontro: $requiredPath" }
}
$localDir = Join-Path $repoDir '.local\uareu'
$nativeDir = Join-Path $localDir 'native-x64'
$extractDir = Join-Path $localDir 'extracted'
New-Item -ItemType Directory -Path $nativeDir, $extractDir -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $localDir 'dpuareu.jar')
Get-ChildItem -LiteralPath $sourceNative -Filter '*.dll' | Copy-Item -Destination $nativeDir
& expand.exe '-F:nex*' $cabPath $extractDir
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron extraer las dependencias nex del runtime.' }
$nex = Join-Path $extractDir 'nex_sdk.dll'
$bytes = [IO.File]::ReadAllBytes($nex)
$peOffset = [BitConverter]::ToInt32($bytes, 0x3c)
if ([BitConverter]::ToUInt16($bytes, $peOffset + 4) -ne 0x8664) { throw 'nex_sdk.dll no es x64.' }
foreach ($name in @('nex_sdk.dll', 'nex_53ov_23.dat', 'nex_license_2.3_53ov.lic')) {
    Copy-Item -LiteralPath (Join-Path $extractDir $name) -Destination $nativeDir
}
Write-Host "SDK 3.2 x64 preparado en $localDir (ignorado por Git)."
Write-Host 'Compruebe el lector: .\gradlew.bat :desktopApp:checkFingerprintReader'
Write-Host 'Abra la app: .\gradlew.bat :desktopApp:run'
