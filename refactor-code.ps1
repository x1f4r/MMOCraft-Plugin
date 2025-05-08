<#
.SYNOPSIS
    Attempts to fix MMOCraft compilation errors by deleting known conflicting/old files
    from the refactoring process. (v1.1 - Corrected path joining)
.DESCRIPTION
    This script deletes specific files and directories identified as duplicates or outdated
    after the MMOCraft refactoring. This should resolve the "duplicate class",
    "protected access", and many "incompatible types" errors.
.NOTES
    Author: Gemini
    Version: 1.1
    !!! WARNING: THIS SCRIPT DELETES FILES AND DIRECTORIES. BACK UP YOUR PROJECT FIRST !!!
#>

# --- Configuration ---
$ProjectRoot = Get-Location # Assumes script is run from the project root
$SrcRoot = Join-Path -Path $ProjectRoot -ChildPath "src\main\java\io\github\x1f4r\mmocraft"

# --- Relative Paths of Files and Directories to Delete ---
# Define arrays with just the relative paths (strings)
$RelativeFilesToDelete = @(
    "MMOCraft.java",
    "player\PlayerStats.java"
# Add other specific old relative file paths here if identified later
)

$RelativeDirectoriesToDelete = @(
    "player\listeners", # Contains duplicate listeners
    "mobs"             # Contains duplicate mob-related classes
)

# --- Safety Check ---
Write-Warning "**********************************************************************"
Write-Warning "           !!! MMOCraft - Automated Error Fix Script !!!             "
Write-Warning "This script will attempt to fix compilation errors by DELETING files"
Write-Warning "and directories identified as old/conflicting:"
Write-Warning "Target Project Root: $($ProjectRoot.Path)"
$RelativeFilesToDelete | ForEach-Object { Write-Warning " - Relative File to Delete: $_" }
$RelativeDirectoriesToDelete | ForEach-Object { Write-Warning " - Relative Directory to Delete: $_" }
Write-Warning "----------------------------------------------------------------------"
Write-Warning ">>> ENSURE YOU HAVE A COMPLETE BACKUP BEFORE PROCEEDING <<<"
Write-Warning "**********************************************************************"

$Confirmation = Read-Host -Prompt "Type 'DELETE_OLD_FILES' to confirm and proceed with deletion"

if ($Confirmation -ne 'DELETE_OLD_FILES') {
    Write-Host "Operation aborted by the user." -ForegroundColor Yellow
    exit
}

Write-Host "Proceeding with deletion..." -ForegroundColor Green

# --- Deletion Logic ---
$ErrorsOccurred = $false

# Delete specific files
foreach ($relativeFile in $RelativeFilesToDelete) {
    # Construct the full path here
    $fullFilePath = Join-Path -Path $SrcRoot -ChildPath $relativeFile
    if (Test-Path -Path $fullFilePath -PathType Leaf) {
        try {
            Remove-Item -Path $fullFilePath -Force -ErrorAction Stop
            Write-Host "Deleted file: $fullFilePath" -ForegroundColor Cyan
        }
        catch {
            Write-Error "Failed to delete file '$fullFilePath': $($_.Exception.Message)"
            $ErrorsOccurred = $true
        }
    }
    else {
        Write-Host "File not found (already deleted?): $fullFilePath" -ForegroundColor Gray
    }
}

# Delete directories
foreach ($relativeDir in $RelativeDirectoriesToDelete) {
    # Construct the full path here
    $fullDirPath = Join-Path -Path $SrcRoot -ChildPath $relativeDir
    if (Test-Path -Path $fullDirPath -PathType Container) {
        try {
            Remove-Item -Path $fullDirPath -Recurse -Force -ErrorAction Stop
            Write-Host "Deleted directory: $fullDirPath" -ForegroundColor Cyan
        }
        catch {
            Write-Error "Failed to delete directory '$fullDirPath': $($_.Exception.Message)"
            $ErrorsOccurred = $true
        }
    }
    else {
        Write-Host "Directory not found (already deleted?): $fullDirPath" -ForegroundColor Gray
    }
}

# --- Completion Message ---
Write-Host "----------------------------------------------------------------------"
if ($ErrorsOccurred) {
    Write-Warning "Script finished, but some errors occurred during deletion. Please check the output above."
}
else {
    Write-Host "Script finished successfully." -ForegroundColor Green
}
Write-Host "The conflicting files/directories should now be removed."
Write-Host "Please try cleaning your build (e.g., 'gradlew clean') and rebuilding the project."
Write-Host "Note: This script does NOT automatically add missing imports. Refer to previous messages for files needing imports."
Write-Host "----------------------------------------------------------------------"

