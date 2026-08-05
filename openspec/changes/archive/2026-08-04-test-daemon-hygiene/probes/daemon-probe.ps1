<#
.SYNOPSIS
Phase 0 probe for the `test-daemon-hygiene` OpenSpec change.

Records the three baseline/verification metrics defined in tasks 1.1/5.1:
  1. nested daemon count   - running Gradle daemons (process list) and the
                             GRADLE_USER_HOME/daemon/<version>/ log-dir state
  2. suite wall time       - wall-clock time of a Gradle test suite invocation
  3. mcpDependencyReport launch count - occurrences of the task in daemon logs

.PARAMETER Action
  snapshot   : print current daemon state (running daemons + log dir) and,
               when -OutFile is given, persist it as JSON.
  walltime   : run the command given in -Command (a single string) and print
               its wall-clock time plus exit code.
  count      : count lines matching -Pattern (default 'mcpDependencyReport')
               in daemon logs under -DaemonDir, optionally limited to logs
               modified since -SinceDays ago.
.PARAMETER DaemonDir
  Directory containing daemon logs (default: $env:USERPROFILE\.gradle\daemon\<version>).
.PARAMETER Version
  Gradle version subdirectory under the daemon dir (default 9.6.1).
.PARAMETER OutFile
  Where to write the JSON snapshot (snapshot action only).
.PARAMETER Command
  The Gradle command line to time (walltime action only), e.g.
  ".\gradlew.bat :test --tests ...".
.PARAMETER Pattern
  Regex for the count action (default 'mcpDependencyReport').
.PARAMETER SinceDays
  Only count log files modified within the last N days (count action).
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('snapshot', 'walltime', 'count')]
    [string]$Action,

    [string]$DaemonDir,
    [string]$Version = '9.6.1',
    [string]$OutFile,
    [string]$Command,
    [string]$Pattern = 'mcpDependencyReport',
    [int]$SinceDays = 365
)

$ErrorActionPreference = 'Stop'

if (-not $DaemonDir) {
    $DaemonDir = Join-Path $env:USERPROFILE ('.gradle\daemon\' + $Version)
}

function Get-GradleDaemons {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'GradleDaemon' } |
        ForEach-Object {
            $cl = $_.CommandLine
            $xmx = if ($cl -match '-Xmx(\S+)') { $matches[1] } else { $null }
            $idle = if ($cl -match 'daemon\.idletimeout=(\d+)') { $matches[1] } else { $null }
            $javaHome = if ($cl -match '-Djava\.home=([^; ]+)') { $matches[1] } else { $null }
            [PSCustomObject]@{
                pid = $_.ProcessId
                javaHome = $javaHome
                xmx = $xmx
                idleTimeoutMs = $idle
                commandLine = $cl.Substring(0, [Math]::Min(200, $cl.Length))
            }
        }
}

function Get-DaemonLogState {
    $logs = @()
    if (Test-Path $DaemonDir) {
        $logs = Get-ChildItem (Join-Path $DaemonDir 'daemon-*.out.log') -ErrorAction SilentlyContinue |
            ForEach-Object {
                $ctx = $null
                # Gradle 9.x writes the DefaultDaemonContext line well past the first lines (e.g. ~line 120),
                # so scan a generous head window for it.
                $head = Get-Content $_.FullName -TotalCount 200 -ErrorAction SilentlyContinue
                $ctxLine = $head | Where-Object { $_ -match 'DefaultDaemonContext\[' } | Select-Object -First 1
                if ($ctxLine -and $ctxLine -match 'javaHome=([^,]+),javaVersion=([0-9]+).*?idleTimeout=(\d+)') {
                    $ctx = [PSCustomObject]@{
                        javaHome = $matches[1]
                        javaVersion = [int]$matches[2]
                        idleTimeoutMs = [long]$matches[3]
                    }
                }
                [PSCustomObject]@{
                    file = $_.Name
                    modified = $_.LastWriteTime.ToString('o')
                    size = $_.Length
                    daemonContext = $ctx
                }
            }
    }
    return $logs
}

switch ($Action) {
    'snapshot' {
        $running = @(Get-GradleDaemons)
        $logs = @(Get-DaemonLogState)
        $result = [PSCustomObject]@{
            timestamp = (Get-Date).ToString('o')
            daemonDir = $DaemonDir
            version = $Version
            runningDaemonCount = $running.Count
            runningDaemons = $running
            daemonLogCount = $logs.Count
            daemonLogs = $logs
        }

        $result | ConvertTo-Json -Depth 6

        if ($OutFile) {
            $dir = Split-Path $OutFile -Parent
            if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            $result | ConvertTo-Json -Depth 6 | Set-Content -Path $OutFile -Encoding utf8
            Write-Host "Snapshot written to $OutFile"
        }

        Write-Host ("Running daemons: {0}; daemon logs: {1}" -f $running.Count, $logs.Count)
    }

    'walltime' {
        if (-not $Command) { throw 'walltime requires -Command' }
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        & ([ScriptBlock]::Create($Command))
        $exit = $LASTEXITCODE
        $sw.Stop()
        [PSCustomObject]@{
            command = $Command
            exitCode = $exit
            wallTimeSeconds = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
        } | ConvertTo-Json
        exit $exit
    }

    'count' {
        if (-not (Test-Path $DaemonDir)) { throw "Daemon dir not found: $DaemonDir" }
        $since = (Get-Date).AddDays(-$SinceDays)
        $logs = Get-ChildItem (Join-Path $DaemonDir 'daemon-*.out.log') |
            Where-Object { $_.LastWriteTime -ge $since }
        $total = 0
        $perLog = @()
        foreach ($log in $logs) {
            $n = (Select-String -Path $log.FullName -Pattern $Pattern -AllMatches -ErrorAction SilentlyContinue | Measure-Object).Count
            if ($n -gt 0) {
                $total += $n
                $perLog += [PSCustomObject]@{ file = $log.Name; modified = $log.LastWriteTime.ToString('o'); matches = $n }
            }
        }
        [PSCustomObject]@{
            pattern = $Pattern
            sinceDays = $SinceDays
            daemonDir = $DaemonDir
            matchingLogCount = $perLog.Count
            totalMatches = $total
            perLog = ($perLog | Select-Object -First 50)
        } | ConvertTo-Json -Depth 4
    }
}
