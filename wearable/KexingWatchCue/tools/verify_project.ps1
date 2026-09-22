[CmdletBinding()]
param([switch]$SourceOnly)

$ErrorActionPreference = 'Stop'
$projectDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectDir

function Fail([string]$Message) {
    throw "校验失败：$Message"
}

$udid = $env:KEXING_DEVICE_UDID
$deviceFile = Join-Path $projectDir 'DEVICE_UDID.txt'
$exampleFile = Join-Path $projectDir 'DEVICE_UDID.txt.example'
if ([string]::IsNullOrWhiteSpace($udid) -and (Test-Path -LiteralPath $deviceFile)) {
    $udid = (Get-Content -Raw -LiteralPath $deviceFile).Trim()
}
if ([string]::IsNullOrWhiteSpace($udid) -and $SourceOnly) {
    $udid = (Get-Content -Raw -LiteralPath $exampleFile).Trim()
}
if ([string]::IsNullOrWhiteSpace($udid)) {
    Fail '缺少 DEVICE_UDID.txt；真机调试/签名前请通过源码外文件或 KEXING_DEVICE_UDID 提供'
}
if ($udid -notmatch '^[0-9A-F]{64}$') { Fail '设备 UDID 必须是 64 位大写十六进制' }
if (-not $SourceOnly -and $udid -eq ('0' * 64)) { Fail '正式校验不能使用占位 UDID' }

$phoneFingerprint = (Get-Content -Raw -LiteralPath (Join-Path $projectDir 'PHONE_CERT_SHA256.txt')).Trim()
if ($phoneFingerprint -notmatch '^[0-9a-f]{64}$') { Fail 'PHONE_CERT_SHA256.txt 必须是 64 位小写十六进制 SHA-256' }
$configPath = Join-Path $projectDir 'entry/src/main/config.json'
$configText = Get-Content -Raw -LiteralPath $configPath
try { $null = $configText | ConvertFrom-Json } catch { Fail "config.json 不是有效 JSON：$($_.Exception.Message)" }
if ($configText -notmatch 'com.classcue.kexing:' -or $configText -notmatch [regex]::Escape($phoneFingerprint)) {
    Fail 'config.json 未包含 Android 正式包名和签名指纹'
}
$peerPath = Join-Path $projectDir 'entry/src/main/js/default/constants/peer.js'
if (-not ((Get-Content -Raw -LiteralPath $peerPath) -match [regex]::Escape($phoneFingerprint))) {
    Fail 'peer.js 未包含手机签名指纹'
}

$sdkPath = Join-Path $projectDir 'entry/src/main/js/default/wearengine/wearengine.js'
$expectedHash = (Get-Content -Raw -LiteralPath "$sdkPath.sha256").Trim().ToLowerInvariant()
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sdkPath).Hash.ToLowerInvariant()
if ($actualHash -ne $expectedHash) { Fail 'wearengine.js SHA-256 不匹配' }

$jsFiles = @(
    'entry/src/main/js/default/app.js',
    'entry/src/main/js/default/constants/peer.js',
    'entry/src/main/js/default/wearengine/compat.js',
    'entry/src/main/js/default/pages/index/history.js',
    'entry/src/main/js/default/pages/index/index.js'
)
foreach ($relativePath in $jsFiles) {
    $path = Join-Path $projectDir $relativePath
    & node --check $path
    if ($LASTEXITCODE -ne 0) { Fail "JavaScript 语法检查失败：$relativePath" }
}

Write-Output '手表工程源码校验通过'
Write-Output "目标设备 UDID：$udid"
Write-Output "手机正式包名：com.classcue.kexing"
Write-Output "手机 APK 证书 SHA-256：$phoneFingerprint"
