Param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"

Write-Host "== MoneyMind dev-check =="

Write-Host "[1/4] Java"
java -version

Write-Host "[2/4] Gradle wrapper"
./gradlew.bat --version | Out-Null
Write-Host "Gradle wrapper OK"

Write-Host "[3/4] Unit test"
./gradlew.bat testSafeDebugUnitTest

if ($Build) {
    Write-Host "[4/4] Assemble"
    ./gradlew.bat assembleSafeDebug
} else {
    Write-Host "[4/4] Assemble skipped (use -Build to include)"
}

Write-Host "Done."

