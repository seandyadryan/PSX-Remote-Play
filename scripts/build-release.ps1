param(
    [string]$KeystorePath = 'D:\KEYSTORE\Psx_Remote_Play.jks',
    [string]$KeyAlias = 'psx_remote_play'
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $KeystorePath -PathType Leaf)) { throw 'Keystore file not found.' }
$original = @{}
foreach ($name in @('PSX_KEYSTORE_PATH','PSX_KEY_ALIAS','PSX_STORE_PASSWORD','PSX_KEY_PASSWORD')) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:PSX_KEYSTORE_PATH = (Resolve-Path -LiteralPath $KeystorePath).Path
    $env:PSX_KEY_ALIAS = $KeyAlias
    if (!$env:PSX_STORE_PASSWORD) {
        $secure = Read-Host 'Keystore password' -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:PSX_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
    if (!$env:PSX_KEY_PASSWORD) { $env:PSX_KEY_PASSWORD = $env:PSX_STORE_PASSWORD }
    Push-Location "$PSScriptRoot/.."
    try {
        & .\gradlew.bat :app:bundleRelease :app:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $original.Keys) { [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process') }
}
