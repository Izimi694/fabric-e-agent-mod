#Requires -Version 5.1
param(
    [switch]$SkipBuild,
    [switch]$Monitor,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$LogFile = "$Root\run\logs\latest.log"
$global:PASS = 0
$global:FAIL = 0

function Assert-Log {
    param($Pattern, $TimeoutSeconds = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $LogFile) {
            $c = Get-Content -Path $LogFile -Tail 200 -ErrorAction SilentlyContinue
            if ($c -match $Pattern) { return $true }
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function check {
    param([string]$Name, [scriptblock]$Block)
    try {
        & $Block
        Write-Host "  [PASS] $Name" -ForegroundColor Green
        $global:PASS++
    } catch {
        Write-Host "  [FAIL] $Name : $($_.Exception.Message)" -ForegroundColor Red
        $global:FAIL++
    }
}

function run-checks {
    Write-Host ""
    Write-Host "==== E2E: Startup Checks ====" -ForegroundColor Cyan

    check "E-Agent init complete" {
        if (-not (Assert-Log "\[E-Agent\] 初始化完成" 60)) {
            throw "E-Agent did not initialize within 60s"
        }
    }

    check "No atomic write warnings" {
        if (Assert-Log "原子写入校验失败" 5) {
            throw "Atomic write validation is still failing"
        }
    }

    check "No startup exceptions" {
        if (Assert-Log "Exception|NullPointerException|Error" 5) {
            throw "Found exception in startup log"
        }
    }

    check "Reflex registry init" {
        if (-not (Assert-Log "先天反射注册表已初始化" 5)) {
            throw "Reflex registry was not initialized"
        }
    }

    check "DomainRouter init" {
        if (-not (Assert-Log "DomainRouter|MetaScheduler" 5)) {
            throw "DomainRouter or MetaScheduler not initialized"
        }
    }
}

if ($Help) {
    Write-Host @"
E2E-Test.ps1 - E-Agent E2E Test

Usage:
  .\test\E2E-Test.ps1              Build + launch + verify
  .\test\E2E-Test.ps1 -SkipBuild   Launch only
  .\test\E2E-Test.ps1 -Monitor     Attach to running client logs
  .\test\E2E-Test.ps1 -Help        Show this help

Steps:
  1. gradlew build
  2. gradlew runClient (starts Minecraft)
  3. Check startup logs for errors
  4. Enter live monitor mode

Manual test commands:
  /ai spawn
  /ai list
  /ai status
  @bot1 help
"@
    exit 0
}

Write-Host "=== E-Agent E2E Test ===" -ForegroundColor Cyan

if (-not $Monitor -and -not $SkipBuild) {
    Write-Host "[1/3] Building..." -ForegroundColor Yellow
    Push-Location $Root
    & .\gradlew.bat build 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] Build failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Build succeeded" -ForegroundColor Green
    Pop-Location
}

if (-not $Monitor) {
    Write-Host "[2/3] Launching Minecraft client..." -ForegroundColor Yellow
    if (Test-Path $LogFile) { Remove-Item $LogFile -Force -ErrorAction SilentlyContinue }
    Push-Location $Root
    $proc = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "runClient" -NoNewWindow -PassThru
    Pop-Location
    Write-Host "Waiting 15s for client startup..." -ForegroundColor Gray
    Start-Sleep -Seconds 15
}

Write-Host "[3/3] Checking startup..." -ForegroundColor Yellow
run-checks

Write-Host ""
Write-Host "==== Results ====" -ForegroundColor Cyan
$total = $global:PASS + $global:FAIL
$status = if ($global:FAIL -eq 0) { "ALL PASS" } else { "$($global:FAIL) FAILURES" }
Write-Host "Pass: $global:PASS/$total  Fail: $global:FAIL  [$status]" -ForegroundColor $(if ($global:FAIL -eq 0) { "Green" } else { "Red" })

if ($Monitor -or (-not $proc.HasExited)) {
    Write-Host ""
    Write-Host "Game is running. Manual test commands:" -ForegroundColor Cyan
    Write-Host "  /ai spawn" -ForegroundColor White
    Write-Host "  /ai list" -ForegroundColor White
    Write-Host "  /ai status" -ForegroundColor White
    Write-Host "  @bot1 hello" -ForegroundColor White
    Write-Host ""
    Write-Host "Press Ctrl+C to stop monitoring (game stays running)" -ForegroundColor Yellow
    try {
        Get-Content -Path $LogFile -Tail 0 -ErrorAction SilentlyContinue -Wait
    } catch {
        Write-Host "Monitoring stopped" -ForegroundColor Yellow
    }
}
