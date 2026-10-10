param([switch]$SkipBuild, [ValidateRange(128, 2048)][int]$HeapMB = 384)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot 'source'
$frontendDir = Join-Path $sourceDir 'frontend'
$runtimeDir = Join-Path $sourceDir 'target/runtime'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

# Fail before launching Java when Docker/WSL is unavailable. Never reset volumes.
Push-Location $sourceDir
try {
    try { docker info --format '{{.ServerVersion}}' 2>$null | Out-Null } catch { throw 'Docker/WSL chưa hoạt động. Mở Docker Desktop và kiểm tra engine; không reset/xóa volume.' }
    if ($LASTEXITCODE -ne 0) { throw 'Docker/WSL chưa hoạt động. Mở Docker Desktop và kiểm tra engine; không reset/xóa volume.' }
    docker compose up -d mysql redis
    if ($LASTEXITCODE -ne 0) { throw 'Không khởi động được MySQL/Redis của dự án.' }
    $healthy = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $states = @(docker inspect --format '{{.State.Health.Status}}' qmh-local-mysql-1 qmh-local-redis-1 2>$null)
        if ($LASTEXITCODE -eq 0 -and $states.Count -eq 2 -and ($states | Where-Object { $_ -ne 'healthy' }).Count -eq 0) { $healthy = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $healthy) { throw 'MySQL/Redis chưa healthy; xem docker compose ps/logs trước khi chạy backend.' }
} finally { Pop-Location }

$privateEnv = Join-Path $sourceDir '.env'
if (-not (Test-Path -LiteralPath $privateEnv)) { throw 'Tạo source/.env từ .env.example và điền cấu hình riêng trước.' }
foreach ($line in Get-Content -LiteralPath $privateEnv -Encoding utf8) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $key = $Matches[1]; $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
$jdkDir = $env:JAVA_HOME
if (-not $jdkDir -and (Test-Path -LiteralPath 'C:/Program Files/Java/jdk-21.0.12')) { $jdkDir = 'C:/Program Files/Java/jdk-21.0.12' }
if (-not $jdkDir) { throw 'Đặt JAVA_HOME tới JDK 21.' }
$javaExe = Join-Path $jdkDir 'bin/java.exe'
$versionText = (& $javaExe --version | Out-String)
if ($versionText -notmatch '(?m)^(java|openjdk) 21([.\s])') { throw 'JAVA_HOME phải trỏ tới JDK 21.' }
$env:JAVA_HOME = $jdkDir
$env:PATH = "$(Join-Path $jdkDir 'bin');$env:PATH"
$env:NODE_OPTIONS = '--max-old-space-size=256 --max-semi-space-size=4'
$env:MAVEN_OPTS = '-XX:ActiveProcessorCount=2 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Xms16m -Xmx128m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m'
if (-not $env:APP_PDF_FONT_PATH -and (Test-Path -LiteralPath 'C:/Windows/Fonts/arial.ttf')) { $env:APP_PDF_FONT_PATH = 'C:/Windows/Fonts/arial.ttf' }
$jar = Join-Path $sourceDir 'target/quan-ly-mua-hang-0.0.1-SNAPSHOT.jar'
$apiPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }
if (Get-NetTCPConnection -State Listen -LocalPort $apiPort -ErrorAction SilentlyContinue) { throw "Cổng backend $apiPort đang được dùng. Kiểm tra tiến trình hiện có; script không tự dừng tiến trình đó." }
$existingFrontend = $null
$frontendListener = Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($frontendListener) {
    $trackedPidFile = Join-Path $runtimeDir 'frontend-local.pid'
    $trackedPid = if (Test-Path -LiteralPath $trackedPidFile) { (Get-Content -LiteralPath $trackedPidFile -Raw).Trim() } else { '' }
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($frontendListener.OwningProcess)"
    $expectedVite = Join-Path $frontendDir 'node_modules/vite/bin/vite.js'
    if ($apiPort -eq 8080 -and $trackedPid -eq [string]$frontendListener.OwningProcess -and $processInfo.CommandLine -match [regex]::Escape($expectedVite)) {
        $existingFrontend = Get-Process -Id $frontendListener.OwningProcess
    } else { throw 'Cổng 5173 đang được dùng bởi tiến trình chưa xác nhận. Kiểm tra trước; script không tự dừng tiến trình đó.' }
}
if (-not $SkipBuild) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { Join-Path $env:TEMP 'qmh-tools/apache-maven-3.9.11/bin/mvn.cmd' }
    if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Cần Maven 3.9+ trong PATH hoặc bản portable của dự án.' }
    $mavenArgs = @('-DskipTests', 'package')
    $cachedRepo = Join-Path $env:TEMP 'qmh-maven-repository'
    if (Test-Path -LiteralPath $cachedRepo) { $mavenArgs = @("-Dmaven.repo.local=$cachedRepo") + $mavenArgs }
    Push-Location $sourceDir
    try { & $mavenPath @mavenArgs; if ($LASTEXITCODE -ne 0) { throw 'Build backend thất bại.' } } finally { Pop-Location }
}
if (-not (Test-Path -LiteralPath $jar)) { throw 'Chưa có JAR. Chạy lại không có -SkipBuild.' }
$vitePath = Join-Path $frontendDir 'node_modules/vite/bin/vite.js'
if (-not (Test-Path -LiteralPath $vitePath)) { throw 'Chạy npm.cmd ci trong source/frontend trước.' }
$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:$apiPort"
$javaArgs = @('-XX:ActiveProcessorCount=2', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xss512k', '-Xms32m', "-Xmx${HeapMB}m", '-XX:MaxMetaspaceSize=160m', '-XX:ReservedCodeCacheSize=32m', '-jar', "`"$jar`"", '--debug=false', '--logging.level.org.springframework.security=INFO')
$backend = Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $sourceDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'backend-local.log') -RedirectStandardError (Join-Path $runtimeDir 'backend-local-error.log')
$apiReady = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($backend.HasExited) { break }
    try { $health = Invoke-RestMethod "http://127.0.0.1:$apiPort/actuator/health" -TimeoutSec 1; if ($health.status -eq 'UP') { $apiReady = $true; break } } catch { }
    Start-Sleep -Milliseconds 500
}
if (-not $apiReady) { if (-not $backend.HasExited) { $backend.Kill() }; throw 'Backend chưa UP. Xem log riêng ở source/target/runtime; không gửi nguyên log có bí mật lên chat.' }
$frontend = if ($existingFrontend) { $existingFrontend } else { Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @("`"$vitePath`"", '--host', '127.0.0.1', '--port', '5173', '--strictPort') -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'frontend-local.log') -RedirectStandardError (Join-Path $runtimeDir 'frontend-local-error.log') }
$frontend.Id | Set-Content -LiteralPath (Join-Path $runtimeDir 'frontend-local.pid')
@{ backendPid = $backend.Id; frontendPid = $frontend.Id; apiPort = $apiPort; checkedAt = (Get-Date).ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeDir 'local-processes.json') -Encoding utf8
$frontendReady = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    if ($frontend.HasExited) { break }
    try { $response = Invoke-WebRequest 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 1; if ($response.StatusCode -eq 200) { $frontendReady = $true; break } } catch { }
    Start-Sleep -Milliseconds 500
}
if (-not $frontendReady) { if (-not $existingFrontend -and -not $frontend.HasExited) { $frontend.Kill() }; throw "Backend UP ở http://localhost:$apiPort, nhưng frontend chưa khởi động được. Xem frontend-local-error.log riêng." }
Write-Host "Backend UP ở http://localhost:$apiPort; frontend HTTP 200 ở http://localhost:5173."
Write-Host 'Thông tin đăng nhập giữ riêng; bootstrap không ghi đè mật khẩu tài khoản đã tồn tại.'
