param([string]$CacheDirectory = "$PSScriptRoot/../app/build/native-provider")
$ErrorActionPreference = 'Stop'
function SourceHash([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
}
$cache = [System.IO.Path]::GetFullPath($CacheDirectory)
New-Item -ItemType Directory -Path $cache -Force | Out-Null
$dependencies = Get-Content "$PSScriptRoot/../app/src/main/cpp/dependencies.json" -Raw | ConvertFrom-Json
foreach ($dependency in $dependencies) {
    $archive = Join-Path $cache "$($dependency.name).tar.gz"
    if (!(Test-Path -LiteralPath $archive) -or (SourceHash $archive) -ne $dependency.sha256) {
        Write-Host "Downloading pinned $($dependency.name) source"
        Invoke-WebRequest -UseBasicParsing -Uri $dependency.url -OutFile "$archive.download"
        if ((SourceHash "$archive.download") -ne $dependency.sha256) {
            throw "Source checksum mismatch for $($dependency.name)"
        }
        Move-Item -LiteralPath "$archive.download" -Destination $archive -Force
    }
    $marker = Join-Path $cache "$($dependency.directory)/.source-verified"
    if (!(Test-Path -LiteralPath $marker)) {
        # Select only needed source trees; the upstream UI archives contain Unix symlinks.
        $arguments = @('-xzf', $archive, '-C', $cache)
        if ($dependency.members) { $arguments += $dependency.members | ForEach-Object { "$($dependency.directory)/$_" } }
        & tar @arguments
        if ($LASTEXITCODE -ne 0) { throw "Cannot extract $($dependency.name) source" }
        Set-Content -LiteralPath $marker -Value $dependency.sha256 -NoNewline
    }
}
# NDK 28 / Clang 19 correctly rejects assigning a pointer-typed NULL to pthread_t.
# Keep this tiny compatibility patch explicit and reproducible against the pinned source.
$congestionSource = Join-Path $cache 'chiaki-ng-ca5379f4504af6e63cf5d32b5626de2425b2382e/lib/src/congestioncontrol.c'
$congestionText = [System.IO.File]::ReadAllText($congestionSource)
if ($congestionText.Contains('control->thread.thread = NULL;')) {
    $congestionText = $congestionText.Replace('control->thread.thread = NULL;', 'control->thread.thread = 0;')
    [System.IO.File]::WriteAllText($congestionSource, $congestionText, (New-Object System.Text.UTF8Encoding($false)))
} elseif (!$congestionText.Contains('control->thread.thread = 0;')) {
    throw 'The pinned Chiaki pthread compatibility patch no longer matches.'
}
# pthread_clockjoin_np is a glibc extension absent from Android's bionic libc.
# This optional timed-join entry is unused by Chiaki's registration/session paths.
$threadSource = Join-Path $cache 'chiaki-ng-ca5379f4504af6e63cf5d32b5626de2425b2382e/lib/src/thread.c'
$threadText = [System.IO.File]::ReadAllText($threadSource)
$threadPatched = [regex]::Replace($threadText, '(?m)^#if !__APPLE__(\r?\n)(?=CHIAKI_EXPORT ChiakiErrorCode chiaki_thread_timedjoin)', '#if !__APPLE__ && !defined(__ANDROID__)$1')
if (!$threadPatched.Contains('#if !__APPLE__ && !defined(__ANDROID__)')) { throw 'Android timed-join compatibility patch did not match.' }
if ($threadPatched -ne $threadText) {
    [System.IO.File]::WriteAllText($threadSource, $threadPatched, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host 'Native protocol sources verified.'
