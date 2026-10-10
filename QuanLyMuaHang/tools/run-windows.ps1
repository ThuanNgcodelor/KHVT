param(
    [Parameter(Position = 0)][ValidateSet('Start', 'Stop', 'Status')][string]$Action = 'Start',
    [switch]$NoBuild,
    [switch]$ExternalDocker,
    [ValidateSet('dev', 'prod')][string]$Profile,
    [ValidateRange(0, 65535)][int]$Port = 0,
    [ValidateRange(128, 2048)][int]$HeapMB = 384,
    [switch]$OpenBrowser
)

# PowerShell 5.1: keep this file UTF-8 with BOM. Never evaluate .env as code.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$sourceDir = Join-Path $repoRoot 'source'
$frontendDir = Join-Path $sourceDir 'frontend'
$runtimeDir = Join-Path $sourceDir 'target/runtime'
$statePath = Join-Path $runtimeDir 'windows-app.json'
$privateEnvPath = Join-Path $sourceDir '.env'
[xml]$projectPom = Get-Content -LiteralPath (Join-Path $sourceDir 'pom.xml') -Raw -Encoding UTF8
$jarName = if ($projectPom.project.build.finalName) { [string]$projectPom.project.build.finalName } else { "$($projectPom.project.artifactId)-$($projectPom.project.version)" }
if ($jarName -match '[\\/]|\$\{') { throw 'Không xác định được tên JAR từ source/pom.xml.' }
$jarPath = Join-Path $sourceDir "target/$jarName.jar"
$mutexHash = [Security.Cryptography.SHA256]::Create()
try { $workspaceHash = ([BitConverter]::ToString($mutexHash.ComputeHash([Text.Encoding]::UTF8.GetBytes($repoRoot.ToLowerInvariant())))).Replace('-', '').Substring(0, 20) } finally { $mutexHash.Dispose() }
$launcherMutex = New-Object Threading.Mutex($false, "Local\KHVT_WINDOWS_$workspaceHash")
$mutexHeld = $false
$launchedProcess = $null
$success = $false

function Read-PrivateEnvironment {
    if (-not (Test-Path -LiteralPath $privateEnvPath -PathType Leaf)) { throw 'Tạo source/.env từ source/.env.example và điền cấu hình riêng trước khi chạy.' }
    $lineNumber = 0
    foreach ($envLine in Get-Content -LiteralPath $privateEnvPath -Encoding UTF8) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($envLine) -or $envLine.TrimStart().StartsWith('#')) { continue }
        if ($envLine -notmatch '^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*=(.*)$') { throw "source/.env có dòng không hợp lệ tại dòng $lineNumber. Dùng KEY=value, không dùng lệnh shell." }
        $envName = $Matches[1]
        $envValue = $Matches[2].Trim()
        if ($envValue.StartsWith('"') -or $envValue.StartsWith("'")) {
            $quote = $envValue.Substring(0, 1)
            if ($envValue.Length -lt 2 -or -not $envValue.EndsWith($quote)) { throw "source/.env thiếu dấu đóng chuỗi tại dòng $lineNumber." }
            $envValue = $envValue.Substring(1, $envValue.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($envName, $envValue, 'Process')
    }
    foreach ($secretName in @('MYSQL_ROOT_PASSWORD', 'DB_PASSWORD', 'REDIS_PASSWORD')) {
        $secretValue = [Environment]::GetEnvironmentVariable($secretName, 'Process')
        if ([string]::IsNullOrWhiteSpace($secretValue) -or $secretValue -match '(?i)^(replace_with|change_me|changeme|your_password|<.*>)') { throw "Điền giá trị riêng cho $secretName trong source/.env. Script không tạo hoặc đổi mật khẩu database." }
    }
    if ($env:ADMIN_BOOTSTRAP_PASSWORD -and $env:ADMIN_BOOTSTRAP_PASSWORD.Length -lt 12) { throw 'ADMIN_BOOTSTRAP_PASSWORD cần ít nhất 12 ký tự; không gửi mật khẩu lên chat.' }
}

function Invoke-NativeLogged([string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory, [string]$LogName, [string]$FailureMessage) {
    Push-Location -LiteralPath $WorkingDirectory
    $oldPreference = $ErrorActionPreference
    try {
        # Native stderr is not a PowerShell terminating error; inspect the exit code.
        $ErrorActionPreference = 'Continue'
        & $Executable @Arguments *> (Join-Path $runtimeDir $LogName)
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference; Pop-Location }
    if ($nativeExit -ne 0) { throw "$FailureMessage Xem log riêng source/target/runtime/$LogName." }
}

function Get-NativeText([string]$Executable, [string[]]$Arguments) {
    $oldPreference = $ErrorActionPreference
    try { $ErrorActionPreference = 'Continue'; $nativeText = (& $Executable @Arguments 2>$null | Out-String); $nativeExit = $LASTEXITCODE } finally { $ErrorActionPreference = $oldPreference }
    if ($nativeExit -ne 0) { return '' }
    return $nativeText.Trim()
}

function Find-Jdk21 {
    $candidates = New-Object 'Collections.Generic.List[string]'
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin/java.exe')) }
    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) { $candidates.Add($pathJava.Source) }
    foreach ($installRoot in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if (-not $installRoot) { continue }
        foreach ($vendorDir in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'Zulu', 'BellSoft', 'Semeru')) {
            $vendorPath = Join-Path $installRoot $vendorDir
            if (-not (Test-Path -LiteralPath $vendorPath -PathType Container)) { continue }
            foreach ($jdkFolder in Get-ChildItem -LiteralPath $vendorPath -Directory -ErrorAction SilentlyContinue) {
                $candidateJava = Join-Path $jdkFolder.FullName 'bin/java.exe'
                if (Test-Path -LiteralPath $candidateJava -PathType Leaf) { $candidates.Add($candidateJava) }
            }
        }
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        $jdkBin = Split-Path -Parent $candidate
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf) -or -not (Test-Path -LiteralPath (Join-Path $jdkBin 'javac.exe') -PathType Leaf)) { continue }
        $javaVersion = Get-NativeText $candidate @('-Xmx32m', '--version')
        if ($javaVersion -match '(?m)^(?:java|openjdk)\s+21(?:[.\s])') { return [IO.Path]::GetFullPath($candidate) }
    }
    throw 'Cần JDK 21. Cài JDK 21 rồi đặt JAVA_HOME hoặc thêm thư mục bin của JDK 21 vào PATH.'
}

function Find-Maven {
    $candidates = New-Object 'Collections.Generic.List[string]'
    $pathMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($pathMaven) { $candidates.Add($pathMaven.Source) }
    foreach ($mavenRoot in @($env:MAVEN_HOME, $env:M2_HOME)) { if ($mavenRoot) { $candidates.Add((Join-Path $mavenRoot 'bin/mvn.cmd')) } }
    # Optional portable installs; no dependency on a particular Windows username/version.
    foreach ($portableRoot in @((Join-Path $repoRoot 'tools'), (Join-Path $env:TEMP 'qmh-tools'))) {
        if (-not (Test-Path -LiteralPath $portableRoot -PathType Container)) { continue }
        foreach ($mavenFolder in Get-ChildItem -LiteralPath $portableRoot -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue) { $candidates.Add((Join-Path $mavenFolder.FullName 'bin/mvn.cmd')) }
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $mavenVersion = Get-NativeText $candidate @('-version')
        if ($mavenVersion -match 'Apache Maven (\d+)\.(\d+)\.') {
            if ([int]$Matches[1] -gt 3 -or ([int]$Matches[1] -eq 3 -and [int]$Matches[2] -ge 9)) { return $candidate }
        }
    }
    throw 'Cần Maven 3.9+ trong PATH hoặc MAVEN_HOME, hoặc giải nén Apache Maven vào tools/apache-maven-<version>.'
}

function Read-ProcessState {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) { return $null }
    try { $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { throw 'File theo dõi windows-app.json không hợp lệ. Kiểm tra tiến trình trước khi sửa file này.' }
    if (-not $state.pid -or -not $state.creationUtcTicks -or -not $state.exePath -or -not $state.jarPath -or [int]$state.port -lt 1 -or [int]$state.port -gt 65535) { throw 'File theo dõi windows-app.json thiếu thông tin nhận diện; script không tự dừng tiến trình.' }
    return $state
}

function Get-OwnedProcess($State) {
    if (-not $State) { return $null }
    $trackedProcess = Get-Process -Id ([int]$State.pid) -ErrorAction SilentlyContinue
    if (-not $trackedProcess) { return $null }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$State.pid)" -ErrorAction Stop
    $expectedJar = [IO.Path]::GetFullPath([string]$State.jarPath)
    if (-not $expectedJar.Equals([IO.Path]::GetFullPath($jarPath), [StringComparison]::OrdinalIgnoreCase) -or
        [string]$trackedProcess.StartTime.ToUniversalTime().Ticks -ne [string]$State.creationUtcTicks -or
        -not ([string]$details.ExecutablePath).Equals([string]$State.exePath, [StringComparison]::OrdinalIgnoreCase) -or
        [string]$details.CommandLine -notmatch ('(?i)(?:^|\s)-jar\s+"?' + [regex]::Escape($expectedJar) + '(?:"|\s|$)')) {
        throw 'PID đang thuộc tiến trình khác hoặc không xác nhận được JAR. Script không dừng Java của IDE hoặc ứng dụng khác.'
    }
    return $trackedProcess
}

function Test-AppHealth([int]$AppPort) {
    try { $health = Invoke-RestMethod -Uri "http://127.0.0.1:$AppPort/actuator/health" -TimeoutSec 2; return $health.status -eq 'UP' } catch { return $false }
}

function Open-AppBrowser([string]$Url) {
    try { Start-Process $Url } catch { Write-Host "Ứng dụng đã chạy; mở trình duyệt tại $Url." }
}

function Assert-PortFree([int]$AppPort) {
    $portProbe = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $AppPort)
    try { $portProbe.Start() } catch { throw "Cổng $AppPort đang được dùng. Dừng bản backend đang chạy bằng VS Code/launcher cũ hoặc chọn -Port khác. Script không tự dừng tiến trình đó." } finally { $portProbe.Stop() }
}

function Assert-BundledJar {
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { throw 'Chưa có JAR. Chạy CHAY_KHVT.cmd start để build cả frontend và backend.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        if (-not $archive.GetEntry('BOOT-INF/classes/static/index.html') -or -not ($archive.Entries | Where-Object { $_.FullName -match '^BOOT-INF/classes/static/assets/.+\.js$' } | Select-Object -First 1)) { throw 'JAR chưa chứa frontend. Chạy lại không có -NoBuild để đóng gói bằng profile windows-web.' }
    } finally { $archive.Dispose() }
}

function Remove-GeneratedStaticDirectory {
    $generatedStatic = [IO.Path]::GetFullPath((Join-Path $sourceDir 'target/classes/static'))
    $repoPrefix = $repoRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $generatedStatic.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase) -or $generatedStatic -ne [IO.Path]::GetFullPath((Join-Path $repoRoot 'source/target/classes/static'))) { throw 'Từ chối xóa thư mục static nằm ngoài đầu ra build đã xác nhận.' }
    $checkPath = $generatedStatic
    while (-not $checkPath.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
        if (Test-Path -LiteralPath $checkPath) {
            $directoryInfo = Get-Item -LiteralPath $checkPath -Force
            if (($directoryInfo.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Đầu ra build có junction/symlink; kiểm tra đường dẫn trước khi build lại.' }
        }
        $checkPath = Split-Path -Parent $checkPath
    }
    if (Test-Path -LiteralPath $generatedStatic -PathType Container) { Remove-Item -LiteralPath $generatedStatic -Recurse -Force }
}

function Ensure-DockerServices {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $dockerCommand) { throw 'Cài Docker Desktop rồi mở Docker Desktop riêng trước khi chạy ứng dụng.' }
    $dockerExe = $dockerCommand.Source
    Invoke-NativeLogged $dockerExe @('info', '--format', '{{.ServerVersion}}') $sourceDir 'windows-docker.log' 'Docker engine chưa sẵn sàng. Mở Docker Desktop và chờ engine chạy.'
    $composeArgs = @('compose', '--project-name', 'qmh-local', '--env-file', $privateEnvPath, '--file', (Join-Path $sourceDir 'docker-compose.yml'))
    if (-not $ExternalDocker) { Invoke-NativeLogged $dockerExe ($composeArgs + @('up', '-d', 'mysql', 'redis')) $sourceDir 'windows-compose.log' 'Không khởi động được MySQL/Redis của dự án.' }
    Push-Location -LiteralPath $sourceDir
    try {
        $containerIdsText = Get-NativeText $dockerExe ($composeArgs + @('ps', '-q', 'mysql', 'redis'))
        $containerIds = @($containerIdsText -split '\r?\n' | Where-Object { $_ -match '^[a-f0-9]{12,64}$' })
        if ($containerIds.Count -ne 2) { throw 'Chưa có đủ mysql/redis đang chạy trong compose qmh-local. Nếu dùng -ExternalDocker, hãy tự chạy docker compose up -d mysql redis trong source trước.' }
        $waitTimer = [Diagnostics.Stopwatch]::StartNew()
        while ($waitTimer.Elapsed.TotalSeconds -lt 120) {
            $statesText = Get-NativeText $dockerExe (@('inspect', '--format', '{{.State.Health.Status}}') + $containerIds)
            $states = @($statesText -split '\r?\n' | Where-Object { $_ })
            if ($states.Count -eq 2 -and @($states | Where-Object { $_ -ne 'healthy' }).Count -eq 0) { return }
            Start-Sleep -Seconds 1
        }
        throw 'MySQL/Redis chưa healthy sau 120 giây. Xem docker compose ps/logs; không xóa volume để xử lý.'
    } finally { Pop-Location }
}

try {
    try { $mutexHeld = $launcherMutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $mutexHeld = $true }
    if (-not $mutexHeld) { throw 'Một lượt chạy/build KHVT khác đang hoạt động trong thư mục này. Chờ lượt đó hoàn tất.' }
    New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
    $state = Read-ProcessState
    $ownedProcess = Get-OwnedProcess $state
    if ($Action -eq 'Stop') {
        if (-not $ownedProcess) { Write-Host 'Không có tiến trình KHVT do launcher này quản lý đang chạy.'; $success = $true; return }
        # Ownership includes PID creation time, executable path and exact JAR path.
        $ownedProcess.Kill()
        if (-not $ownedProcess.WaitForExit(10000)) { throw 'Tiến trình chưa dừng; giữ file theo dõi để kiểm tra lại.' }
        Remove-Item -LiteralPath $statePath -Force
        Write-Host 'Đã dừng ứng dụng KHVT. MySQL/Redis và Docker Desktop tiếp tục chạy.'
        $success = $true
        return
    }
    if ($Action -eq 'Status') {
        if (-not $ownedProcess) { Write-Host 'Ứng dụng KHVT do launcher này quản lý chưa chạy.' }
        else {
            $healthLabel = if (Test-AppHealth ([int]$state.port)) { 'UP' } else { 'chưa UP' }
            Write-Host "KHVT PID $($state.pid), health $healthLabel, địa chỉ $($state.url)."
        }
        $success = $true
        return
    }
    if ($ownedProcess) {
        if (($Port -gt 0 -and $Port -ne [int]$state.port) -or ($Profile -and $Profile -ne [string]$state.profile)) { throw 'KHVT đã chạy với port/profile khác. Dùng CHAY_KHVT.cmd stop trước khi thay đổi port/profile.' }
        if (-not (Test-AppHealth ([int]$state.port))) { throw 'Ứng dụng được theo dõi vẫn chạy nhưng chưa healthy. Dùng CHAY_KHVT.cmd stop rồi kiểm tra log trước khi chạy lại.' }
        Write-Host "KHVT đang chạy: $($state.url). Không build/chạy thêm tiến trình."
        if ($OpenBrowser) { Open-AppBrowser ([string]$state.url) }
        $success = $true
        return
    }
    if ($state) { Remove-Item -LiteralPath $statePath -Force }
    Read-PrivateEnvironment
    $activeProfile = if ($Profile) { $Profile } elseif ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
    if ($activeProfile -notin @('dev', 'prod')) { throw 'Launcher Windows hỗ trợ profile dev hoặc prod. Chọn -Profile dev/prod hoặc sửa SPRING_PROFILES_ACTIVE trong source/.env.' }
    $appPort = if ($Port -gt 0) { $Port } elseif ($env:SERVER_PORT -match '^\d+$') { [int]$env:SERVER_PORT } elseif (-not $env:SERVER_PORT) { 8080 } else { throw 'SERVER_PORT phải là số từ 1 đến 65535.' }
    if ($appPort -lt 1 -or $appPort -gt 65535) { throw 'SERVER_PORT phải từ 1 đến 65535.' }
    Assert-PortFree $appPort
    $env:SERVER_PORT = [string]$appPort
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SPRING_PROFILES_ACTIVE = $activeProfile
    if ($activeProfile -eq 'prod') {
        $publicOrigin = $null
        if (-not [Uri]::TryCreate($env:APP_FRONTEND_URL, [UriKind]::Absolute, [ref]$publicOrigin) -or $publicOrigin.Scheme -ne 'https' -or $publicOrigin.AbsolutePath -ne '/' -or $publicOrigin.Query -or $publicOrigin.Fragment -or $publicOrigin.UserInfo) { throw 'Profile prod cần APP_FRONTEND_URL=https://ten-mien-cua-ban trong source/.env, chỉ gồm origin. Cloudflare Tunnel phải được cấu hình riêng.' }
        $env:SESSION_COOKIE_SECURE = 'true'
        $appUrl = $publicOrigin.GetLeftPart([UriPartial]::Authority)
        $env:APP_FRONTEND_URL = $appUrl
    } else {
        $env:APP_FRONTEND_URL = "http://localhost:$appPort"
        $env:SESSION_COOKIE_SECURE = 'false'
        $appUrl = $env:APP_FRONTEND_URL
    }
    $env:NODE_OPTIONS = '--max-old-space-size=256 --max-semi-space-size=4'
    $env:MAVEN_OPTS = '-XX:ActiveProcessorCount=2 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Xms16m -Xmx128m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m'
    if (-not $env:APP_PDF_FONT_PATH -or -not (Test-Path -LiteralPath $env:APP_PDF_FONT_PATH -PathType Leaf)) {
        $windowsArial = Join-Path $env:WINDIR 'Fonts/arial.ttf'
        if (-not (Test-Path -LiteralPath $windowsArial -PathType Leaf)) { throw 'Cần đặt APP_PDF_FONT_PATH tới font Unicode TTF tồn tại trên máy Windows này.' }
        $env:APP_PDF_FONT_PATH = $windowsArial
    }
    $javaExe = Find-Jdk21
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
    $env:PATH = "$(Split-Path -Parent $javaExe);$env:PATH"
    Write-Host 'Kiểm tra Docker Desktop và MySQL/Redis của dự án...'
    Ensure-DockerServices
    if (-not $NoBuild) {
        $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
        $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if (-not $nodeCommand -or -not $npmCommand) { throw 'Cần Node.js 22+ (kèm npm) trong PATH để build frontend.' }
        $nodeVersion = Get-NativeText $nodeCommand.Source @('--version')
        if ($nodeVersion -notmatch '^v(\d+)\.' -or [int]$Matches[1] -lt 22) { throw 'Cần Node.js 22+ trong PATH để build frontend.' }
        $mavenExe = Find-Maven
        $env:VITE_API_BASE_URL = '/api'
        Write-Host 'Cài dependency frontend từ package-lock.json...'
        Invoke-NativeLogged $npmCommand.Source @('ci', '--no-audit', '--no-fund') $frontendDir 'windows-npm-ci.log' 'npm ci thất bại.'
        Write-Host 'Build frontend React...'
        Invoke-NativeLogged $npmCommand.Source @('run', 'build') $frontendDir 'windows-frontend-build.log' 'Build frontend thất bại.'
        if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'dist/index.html') -PathType Leaf)) { throw 'Frontend build chưa sinh dist/index.html.' }
        Remove-GeneratedStaticDirectory
        $mavenArgs = @('-Pwindows-web', '-DskipTests', 'package')
        if ($env:QMH_MAVEN_REPOSITORY) { $mavenArgs = @("-Dmaven.repo.local=$($env:QMH_MAVEN_REPOSITORY)") + $mavenArgs }
        elseif (Test-Path -LiteralPath (Join-Path $env:TEMP 'qmh-maven-repository') -PathType Container) { $mavenArgs = @("-Dmaven.repo.local=$(Join-Path $env:TEMP 'qmh-maven-repository')") + $mavenArgs }
        Write-Host 'Build backend và đóng gói frontend vào JAR (không chạy bộ test)...'
        Invoke-NativeLogged $mavenExe $mavenArgs $sourceDir 'windows-backend-build.log' 'Build/đóng gói backend thất bại.'
    }
    Assert-BundledJar
    # Bind conflicts can appear while a long build is running: check again.
    Assert-PortFree $appPort
    $javaArgs = @('-XX:ActiveProcessorCount=2', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-Xss512k', '-Xms32m', "-Xmx${HeapMB}m", '-XX:MaxMetaspaceSize=160m', '-XX:ReservedCodeCacheSize=32m', '-jar', "`"$jarPath`"", '--debug=false', '--logging.level.org.springframework.security=INFO')
    Write-Host 'Khởi động ứng dụng web/API...'
    $launchedProcess = Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $sourceDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'windows-app.log') -RedirectStandardError (Join-Path $runtimeDir 'windows-app-error.log')
    $processRecord = [ordered]@{ pid = $launchedProcess.Id; creationUtcTicks = [string]$launchedProcess.StartTime.ToUniversalTime().Ticks; exePath = $javaExe; jarPath = [IO.Path]::GetFullPath($jarPath); port = $appPort; profile = $activeProfile; url = $appUrl; startedAtUtc = [DateTime]::UtcNow.ToString('o') }
    $processRecord | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
    $readyTimer = [Diagnostics.Stopwatch]::StartNew()
    $appReady = $false
    while ($readyTimer.Elapsed.TotalSeconds -lt 60) {
        $launchedProcess.Refresh()
        if ($launchedProcess.HasExited) { break }
        if (Test-AppHealth $appPort) { $appReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $appReady) { throw 'Backend chưa UP sau khi khởi động. Xem windows-app.log/windows-app-error.log riêng; đừng gửi log chứa thông tin nhạy cảm.' }
    $pageResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$appPort/login" -UseBasicParsing -TimeoutSec 5
    if ($pageResponse.StatusCode -ne 200 -or $pageResponse.Content -notmatch 'id=["\x27]root["\x27]') { throw 'Backend UP nhưng giao diện đã đóng gói chưa phục vụ được /login. Kiểm tra cấu hình static/SPA.' }
    Write-Host "KHVT đã chạy: $appUrl"
    Write-Host 'Một tiến trình Java phục vụ cả giao diện và API. Dùng CHAY_KHVT.cmd stop/status để quản lý.'
    if ($activeProfile -eq 'prod') { Write-Host "Origin cục bộ cho Cloudflare Tunnel: http://127.0.0.1:$appPort. Script không tự tạo hoặc kết nối Tunnel." }
    if ($OpenBrowser) { Open-AppBrowser $appUrl }
    $success = $true
} catch {
    Write-Host ("KHVT: " + $_.Exception.Message) -ForegroundColor Red
    if ($launchedProcess) {
        $launchedProcess.Refresh()
        if (-not $launchedProcess.HasExited) { $launchedProcess.Kill(); $null = $launchedProcess.WaitForExit(10000) }
        if (Test-Path -LiteralPath $statePath) { Remove-Item -LiteralPath $statePath -Force }
    }
    exit 1
} finally {
    if ($mutexHeld) { $launcherMutex.ReleaseMutex() }
    $launcherMutex.Dispose()
}
