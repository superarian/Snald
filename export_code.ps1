# ==========================================================
# BYTEMANTIS AUTOMATIC CODE EXPORTER
# Scans your project for all Kotlin and Layout files automatically
# ==========================================================

$outputFile = "SNALD_Full_Source_Code.txt"
$projectRoot = Get-Location
$date = Get-Date -Format "MM/dd/yyyy HH:mm:ss"

# 1. Create/Clear the output file with a Header
$header = @"
============================================
PROJECT: SNALD (Game Hub)
EXPORT DATE: $date
INCLUDES: Snald, Ludo, Core, and Menu
============================================
"@
Set-Content -Path $outputFile -Value $header

# Function to format and append file content
function Append-FileContent {
    param (
        [string]$filePath
    )
    
    # Get relative path for cleaner reading
    $relativePath = $filePath.Substring($projectRoot.Path.Length)
    
    # Read content
    $content = Get-Content -Path $filePath -Raw
    
    # Create the separator block
    $block = @"


################################################################
FILE PATH: $relativePath
################################################################


$content
"@
    # Append to the master file
    Add-Content -Path $outputFile -Value $block
    Write-Host "Exported: $relativePath" -ForegroundColor Cyan
}

# ==========================================================
# 2. EXPORT KOTLIN FILES (.kt)
# Recursively finds ALL .kt files in app/src/main/java
# ==========================================================
Write-Host "`nScanning for Kotlin files..." -ForegroundColor Yellow
$kotlinFiles = Get-ChildItem -Path ".\app\src\main\java" -Filter "*.kt" -Recurse

foreach ($file in $kotlinFiles) {
    Append-FileContent -filePath $file.FullName
}

# ==========================================================
# 3. EXPORT XML LAYOUTS (.xml)
# Recursively finds ALL .xml files in app/src/main/res/layout
# ==========================================================
Write-Host "`nScanning for Layout files..." -ForegroundColor Yellow
$layoutFiles = Get-ChildItem -Path ".\app\src\main\res\layout" -Filter "*.xml" -Recurse

foreach ($file in $layoutFiles) {
    Append-FileContent -filePath $file.FullName
}

# ==========================================================
# 4. EXPORT MANIFEST (Important for Activity changes)
# ==========================================================
Write-Host "`nExporting Manifest..." -ForegroundColor Yellow
$manifestPath = Join-Path $projectRoot "app\src\main\AndroidManifest.xml"
if (Test-Path $manifestPath) {
    Append-FileContent -filePath $manifestPath
}

Write-Host "`n============================================" -ForegroundColor Green
Write-Host "SUCCESS! All codes saved to: $outputFile" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green