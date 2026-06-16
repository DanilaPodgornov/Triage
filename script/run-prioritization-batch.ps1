param(
    [int]$Runs = 10,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Project = "B2C",
    [string]$Model = "",
    [int]$PollIntervalSeconds = 2,
    [int]$PauseBetweenRunsSeconds = 2,
    [string[]]$TestKeys = @(
        "SQM-T1452",
        "SQM-T1269",
        "SQM-T1267",
        "SQM-T1268",
        "SQM-T1270"
    ),
    [string]$CyclesDir = ""
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($CyclesDir)) {
    $CyclesDir = Join-Path (Split-Path $PSScriptRoot -Parent) "suggested_test_cycles"
}
$CyclesDir = [System.IO.Path]::GetFullPath($CyclesDir)

function Get-AiSortOrder {
    param([string]$CycleFilePath)

    $cycle = Get-Content -Raw -Path $CycleFilePath | ConvertFrom-Json
    $ordered = $cycle.tests | Sort-Object { [double]$_.aiSort } -Descending
    return ($ordered | ForEach-Object { $_.key }) -join " -> "
}

function Wait-PrioritizationJob {
    param(
        [string]$JobId,
        [int]$PollIntervalSeconds
    )

    $statusUri = "$BaseUrl/api/prioritize/jobs/$([uri]::EscapeDataString($JobId))"
    while ($true) {
        $jobStatus = Invoke-RestMethod -Uri $statusUri -Method Get
        switch ($jobStatus.status) {
            "COMPLETED" { return $jobStatus }
            "FAILED" { return $jobStatus }
            default {
                Start-Sleep -Seconds $PollIntervalSeconds
            }
        }
    }
}

Write-Host "Batch prioritization: $Runs run(s), project=$Project, tests=$($TestKeys.Count)"
Write-Host "Base URL: $BaseUrl"
Write-Host "Cycles directory: $CyclesDir"
if (-not [string]::IsNullOrWhiteSpace($Model)) {
    Write-Host "Active model: $Model"
    $modelBody = @{ model = $Model } | ConvertTo-Json -Compress
    $modelResponse = Invoke-RestMethod -Uri "$BaseUrl/api/llm/models/active" -Method Put `
        -ContentType "application/json; charset=utf-8" -Body $modelBody
    if ($modelResponse.activeModel -ne $Model) {
        throw "Failed to set active model to $Model (got $($modelResponse.activeModel))."
    }
}
else {
    $active = (Invoke-RestMethod -Uri "$BaseUrl/api/llm/models" -Method Get).activeModel
    Write-Host "Active model (unchanged): $active"
}
Write-Host ""

$results = @()

for ($run = 1; $run -le $Runs; $run++) {
    Write-Host "=== Run $run / $Runs ==="

    $body = @{
        project       = $Project
        testCaseKeys  = $TestKeys
    } | ConvertTo-Json -Compress

  try {
        $start = Invoke-RestMethod -Uri "$BaseUrl/api/prioritize" -Method Post `
            -ContentType "application/json; charset=utf-8" -Body $body

        $jobId = $start.jobId
        if ([string]::IsNullOrWhiteSpace($jobId)) {
            throw "Prioritization start response did not include jobId."
        }

        Write-Host "Started job: $jobId (totalTests=$($start.totalTests))"

        $jobStatus = Wait-PrioritizationJob -JobId $jobId -PollIntervalSeconds $PollIntervalSeconds

        $row = [ordered]@{
            Run         = $run
            JobId       = $jobId
            Status      = $jobStatus.status
            CycleKey    = $jobStatus.cycleKey
            ElapsedMs   = $jobStatus.elapsedMillis
            AiSortOrder = ""
            Error       = $jobStatus.errorMessage
            CycleFile   = ""
        }

        if ($jobStatus.status -eq "COMPLETED") {
            if ([string]::IsNullOrWhiteSpace($jobStatus.cycleKey)) {
                throw "Job completed but cycleKey is missing."
            }

            $cycleFile = Join-Path $CyclesDir "$($jobStatus.cycleKey).json"
            if (-not (Test-Path -LiteralPath $cycleFile)) {
                throw "Expected cycle file not found: $cycleFile"
            }

            $row.CycleFile = $cycleFile
            $row.AiSortOrder = Get-AiSortOrder -CycleFilePath $cycleFile
            Write-Host "COMPLETED: $($jobStatus.cycleKey) in $($jobStatus.elapsedMillis) ms"
            Write-Host "Order: $($row.AiSortOrder)"
        }
        else {
            Write-Host "FAILED: $($jobStatus.errorMessage)"
        }

        $results += [pscustomobject]$row
    }
    catch {
        Write-Host "ERROR on run ${run}: $($_.Exception.Message)"
        $results += [pscustomobject]@{
            Run         = $run
            JobId       = ""
            Status      = "ERROR"
            CycleKey    = ""
            ElapsedMs   = 0
            AiSortOrder = ""
            Error       = $_.Exception.Message
            CycleFile   = ""
        }
    }

    if ($run -lt $Runs -and $PauseBetweenRunsSeconds -gt 0) {
        Start-Sleep -Seconds $PauseBetweenRunsSeconds
    }
}

Write-Host ""
Write-Host "=== Summary ==="
$results | Format-Table -AutoSize Run, Status, CycleKey, ElapsedMs, Error

$completed = @($results | Where-Object { $_.Status -eq "COMPLETED" })
$failed = @($results | Where-Object { $_.Status -ne "COMPLETED" })

Write-Host ""
Write-Host "Completed: $($completed.Count) / $Runs"
Write-Host "Failed: $($failed.Count) / $Runs"

if ($completed.Count -gt 0) {
    Write-Host ""
    Write-Host "Cycle keys:"
    $completed | ForEach-Object { Write-Host "  - $($_.CycleKey)" }
}

if ($failed.Count -gt 0) {
    exit 1
}
