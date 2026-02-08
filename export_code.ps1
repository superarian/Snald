# SNALD Code Exporter Script
# This script finds all your Kotlin and XML code and saves it to one text file.

$outputFile = "SNALD_Source_Code.txt"
$rootPath = Get-Location

# 1. Start the file with a Header
Set-Content -Path $outputFile -Value "============================================"
Add-Content -Path $outputFile -Value "PROJECT: SNALD (Snake and Ladder)"
Add-Content -Path $outputFile -Value "EXPORT DATE: $(Get-Date)"
Add-Content -Path $outputFile -Value "============================================`n"

Write-Host "Scanning for files in: $rootPath" -ForegroundColor Cyan

# 2. Define what files we want (Kotlin and XML layouts)
# We specifically look inside 'app\src\main' to avoid build files and test files.
$targetFolder = Join-Path $rootPath "app\src\main"

if (Test-Path $targetFolder) {
    $files = Get-ChildItem -Path $targetFolder -Recurse -Include *.kt, *.xml
    
    foreach ($file in $files) {
        # Skip weird generated files or hidden files
        if ($file.FullName -match "generated" -or $file.FullName -match "build") { continue }

        $relativePath = $file.FullName.Substring($rootPath.Path.Length)
        
        Write-Host "Exporting: $relativePath" -ForegroundColor Green

        # 3. Write the File Name as a separator
        Add-Content -Path $outputFile -Value "`n"
        Add-Content -Path $outputFile -Value "################################################################"
        Add-Content -Path $outputFile -Value "FILE PATH: $relativePath"
        Add-Content -Path $outputFile -Value "################################################################"
        Add-Content -Path $outputFile -Value "`n"

        # 4. Write the Code Content
        Get-Content $file.FullName | Add-Content -Path $outputFile
    }

    Write-Host "`nSUCCESS! All code saved to: $outputFile" -ForegroundColor Yellow
}
else {
    Write-Host "ERROR: Could not find 'app\src\main'. Are you running this in the Project Root folder?" -ForegroundColor Red
}

# 5. Pause so you can see the result
Read-Host "Press Enter to exit..."