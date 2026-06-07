<#
.SYNOPSIS
  Seed (or clear) demo workout sessions on a connected device/emulator so the
  3D muscle heatmap shows colors. DEBUG build only (uses `adb run-as`).

.DESCRIPTION
  Builds a WorkoutStateDto JSON with three sessions whose endTimes are relative
  to *now*, so recovery colors land predictably:
    - Push  (~6h ago)  -> Pectoraux / Triceps / Epaules        => RED (fatigued)
    - Pull  (~36h ago) -> Dos / Biceps                         => AMBER (recovering)
    - Legs  (~80h ago) -> Quadriceps / Ischios / Fessiers / Mollets => GREEN (ready)
  Muscles never trained stay neutral grey. Writes it into the app's private
  files dir and restarts the app so the repository reloads it.

.EXAMPLE
  pwsh -File tools/seed-demo-workouts.ps1            # seed demo sessions
  pwsh -File tools/seed-demo-workouts.ps1 -Clear     # remove demo data (back to defaults)
#>
param([switch]$Clear)

$ErrorActionPreference = 'Stop'
$pkg = 'com.example.goattracker'
$dst = "/data/data/$pkg/files/workouts.json"

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $adb)) { throw "adb introuvable. Renseigne le SDK Android." }

& $adb wait-for-device | Out-Null

if ($Clear) {
    & $adb shell "run-as $pkg sh -c 'rm -f $dst'"
    & $adb shell am force-stop $pkg
    Write-Host "Demo data supprimee. Relance l'app : les exercices par defaut seront recrees."
    return
}

# Device epoch (ms) so timestamps align with the app's System.currentTimeMillis()
$nowSec = [long]((& $adb shell date +%s).Trim())
$now = $nowSec * 1000L
function ms([double]$h){ [long]($now - [long]($h*3600000)) }
function nset($n,$w,$r){ [ordered]@{ id=[guid]::NewGuid().ToString(); setNumber=$n; weight=$w; reps=$r; durationSeconds=0; distanceKm=0; isCompleted=$true } }
function nex($name,$cat,$muscle,$track,$sets){ [ordered]@{ id=[guid]::NewGuid().ToString(); exercise=[ordered]@{ id=[guid]::NewGuid().ToString(); name=$name; category=$cat; primaryMuscle=$muscle; trackingType=$track; notes=""; restTimeSeconds=90 }; sets=$sets } }
function nsess($name,$startH,$endH,$exs){ [ordered]@{ id=[guid]::NewGuid().ToString(); startTime=(ms $startH); endTime=(ms $endH); name=$name; exercises=$exs } }

$push = nsess "Demo Push" 7 6 @(
  (nex "Developpe Couche" "PUSH" "Pectoraux" "WEIGHT_REPS" @((nset 1 100 5),(nset 2 100 5),(nset 3 90 8),(nset 4 90 8))),
  (nex "Extension Triceps" "PUSH" "Triceps" "WEIGHT_REPS" @((nset 1 30 12),(nset 2 30 12),(nset 3 25 15))),
  (nex "Developpe Militaire" "PUSH" "Epaules" "WEIGHT_REPS" @((nset 1 40 8),(nset 2 40 8),(nset 3 35 10))))
$pull = nsess "Demo Pull" 37 36 @(
  (nex "Tractions" "PULL" "Dos" "BODYWEIGHT_REPS" @((nset 1 0 10),(nset 2 0 8),(nset 3 0 6))),
  (nex "Curl Biceps" "PULL" "Biceps" "WEIGHT_REPS" @((nset 1 20 12),(nset 2 20 12),(nset 3 18 14))))
$legs = nsess "Demo Legs" 81 80 @(
  (nex "Squat" "LEG" "Quadriceps" "WEIGHT_REPS" @((nset 1 120 8),(nset 2 120 8),(nset 3 110 10),(nset 4 110 10),(nset 5 100 12))),
  (nex "Souleve Jambes Tendues" "LEG" "Ischios" "WEIGHT_REPS" @((nset 1 80 10),(nset 2 80 10),(nset 3 70 12),(nset 4 70 12))),
  (nex "Hip Thrust" "LEG" "Fessiers" "WEIGHT_REPS" @((nset 1 100 12),(nset 2 100 12),(nset 3 90 15))),
  (nex "Mollets Debout" "LEG" "Mollets" "WEIGHT_REPS" @((nset 1 60 15),(nset 2 60 15),(nset 3 50 20),(nset 4 50 20))))

$exLib = @(
  [ordered]@{ id="36dc3e59-183f-4744-b9c4-887d7b1f7bb9"; name="Developpe Couche"; category="PUSH"; primaryMuscle="Pectoraux"; trackingType="WEIGHT_REPS"; notes=""; restTimeSeconds=120 },
  [ordered]@{ id="fc7e5763-f257-46a3-aa63-a0ae1b273201"; name="Tractions Pronation"; category="PULL"; primaryMuscle="Dos"; trackingType="BODYWEIGHT_REPS"; notes=""; restTimeSeconds=120 },
  [ordered]@{ id="fe6a462c-e32d-40a8-87d0-8d6b5bb231ae"; name="Squat Barre"; category="LEG"; primaryMuscle="Quadriceps"; trackingType="WEIGHT_REPS"; notes=""; restTimeSeconds=150 })

$state = [ordered]@{ schemaVersion=1; exercises=$exLib; sessions=@($push,$pull,$legs) }
$tmp = Join-Path $env:TEMP 'gt_seed.json'
[IO.File]::WriteAllText($tmp, ($state | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

# Push INTO the app uid: the redirect must run inside run-as (single-quoted), else the
# outer adb shell (shell uid) owns the '>' and write is denied.
Get-Content $tmp -Raw | & $adb shell "run-as $pkg sh -c 'cat > $dst'"
$n = (& $adb shell "run-as $pkg sh -c 'grep -c Demo $dst'").Trim()
& $adb shell am force-stop $pkg
Write-Host "Seede $n sessions demo. Ouvre l'app -> Profil -> Carte musculaire 3D."
