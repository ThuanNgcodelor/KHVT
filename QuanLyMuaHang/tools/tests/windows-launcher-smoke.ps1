param()
$ErrorActionPreference = 'Stop'
$launcherPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'run-windows.ps1'
$tokens = $null
$errors = $null
[Management.Automation.Language.Parser]::ParseFile($launcherPath, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count) { throw 'PowerShell parser rejected the launcher.' }
$launcherBytes = [IO.File]::ReadAllBytes($launcherPath)
if ($launcherBytes.Length -lt 3 -or $launcherBytes[0] -ne 239 -or $launcherBytes[1] -ne 187 -or $launcherBytes[2] -ne 191) { throw 'The launcher requires UTF-8 BOM for PowerShell 5.1.' }

# Status imports the functions without loading .env, building, touching Docker,
# launching Java or stopping any process. It also exercises the real entry path.
. $launcherPath -Action Status
$passed = 2
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $busyPort = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $busyRejected = $false
    try { Assert-PortFree $busyPort } catch { $busyRejected = $_.Exception.Message -match 'Script không tự dừng tiến trình đó' }
    if (-not $busyRejected) { throw 'An occupied port was not rejected safely.' }
    if (-not $listener.Server.IsBound) { throw 'The port owner was affected by the probe.' }
    $passed++
} finally { $listener.Stop() }
Assert-PortFree $busyPort
$passed++

# A real PowerShell process has a valid PID/time/executable, but does not run
# the application JAR. A forged state must not establish process ownership.
$testProcess = Get-Process -Id $PID
$testDetails = Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
$forgedState = [pscustomobject]@{ pid = $PID; creationUtcTicks = [string]$testProcess.StartTime.ToUniversalTime().Ticks; exePath = $testDetails.ExecutablePath; jarPath = $jarPath; port = 8080 }
$identityRejected = $false
try { $null = Get-OwnedProcess $forgedState } catch { $identityRejected = $_.Exception.Message -match 'Script không dừng Java của IDE' }
if (-not $identityRejected -or -not (Get-Process -Id $PID -ErrorAction SilentlyContinue)) { throw 'A process without the exact application JAR was not rejected safely.' }
$passed++

$reusedPidState = [pscustomobject]@{ pid = $PID; creationUtcTicks = '1'; exePath = $testDetails.ExecutablePath; jarPath = $jarPath; port = 8080 }
$reusedRejected = $false
try { $null = Get-OwnedProcess $reusedPidState } catch { $reusedRejected = $_.Exception.Message -match 'Script không dừng Java của IDE' }
if (-not $reusedRejected) { throw 'A stale/reused PID creation time was not rejected safely.' }
$passed++
Write-Host "Windows launcher smoke: $passed checks passed. No Java/Docker/data changes."
