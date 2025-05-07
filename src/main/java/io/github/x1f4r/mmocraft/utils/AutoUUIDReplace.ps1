# AutoUUIDReplace.ps1
# Script to replace placeholder UUIDs in Java files.

param (
# Specifies the root directory of the Java source files.
# Defaults to '.\src\main\java' relative to where the script is run from,
# but it's best to run this script from the project root.
    [string]$ProjectJavaRoot = ".\src\main\java"
)

Write-Host "Starting UUID placeholder replacement script..." -ForegroundColor Yellow
Write-Host "IMPORTANT: Ensure you have backed up your project!" -ForegroundColor Red
Read-Host -Prompt "Press Enter to continue if you have backed up your project, or CTRL+C to abort"

# Validate the project Java root path
if (-not (Test-Path $ProjectJavaRoot)) {
    Write-Error "Project Java root path not found: $ProjectJavaRoot. Please ensure you are running the script from your project's root directory or specify the -ProjectJavaRoot parameter correctly."
    exit 1
}

# Get all Java files recursively
$javaFiles = Get-ChildItem -Path $ProjectJavaRoot -Recurse -Filter "*.java"
# Define the exact prefix for placeholder UUIDs
$placeholderPrefix = "YOUR_UNIQUE_UUID_HERE"
$totalReplacements = 0

# Process each Java file
foreach ($file in $javaFiles) {
    Write-Verbose "Processing file: $($file.FullName)"
    $originalContent = Get-Content -Path $file.FullName -Raw
    $fileModified = $false
    $lines = $originalContent -split '\r?\n' # Handles Windows (CRLF) and Unix (LF) newlines
    $modifiedLines = @()
    $lineNumber = 0

    # Process each line in the current file
    foreach ($line in $lines) {
        $lineNumber++
        $modifiedLine = $line
        # Regex to find UUID.fromString("some-uuid-string")
        $match = [regex]::Match($line, 'UUID\.fromString\("([^"]+)"\)')

        if ($match.Success) {
            $currentUuidString = $match.Groups[1].Value

            # Using -f format operator for robust string construction
            $verboseMsgLine = '  Found potential UUID string on line {0}: "{1}"' -f $lineNumber, $currentUuidString
            Write-Verbose $verboseMsgLine

            # Check if the found UUID string starts with the placeholder prefix
            if ($currentUuidString.StartsWith($placeholderPrefix)) {
                $newGuid = [guid]::NewGuid().ToString()
                $modifiedLine = $line.Replace($currentUuidString, $newGuid)

                Write-Host ("  Replaced placeholder in {0} (Line {1}):" -f $file.Name, $lineNumber) -ForegroundColor Green

                $oldMsgLine = '    Old: "{0}"' -f $currentUuidString
                Write-Host $oldMsgLine

                $newMsgLine = '    New: "{0}"' -f $newGuid
                Write-Host $newMsgLine

                $fileModified = $true
                $totalReplacements++
            } else {
                # Single quotes in the format string are doubled to be treated as literal single quotes
                $skipMsgLine = '    Skipping ''"{0}"'' as it''s not a recognized placeholder.' -f $currentUuidString
                Write-Verbose $skipMsgLine
            }
        }
        $modifiedLines += $modifiedLine
    }

    # If the file was modified, save the changes
    if ($fileModified) {
        Write-Verbose "Saving changes to: $($file.FullName)"
        # Join lines with the system's newline character
        $newContentForFile = $modifiedLines -join [System.Environment]::NewLine

        # Ensure the file ends with a newline, which is standard for text/source files.
        if ($newContentForFile.Length -gt 0 -and !$newContentForFile.EndsWith([System.Environment]::NewLine)) {
            $newContentForFile += [System.Environment]::NewLine
        }

        # Save the modified content back to the file with UTF-8 encoding.
        # -NoNewline:$false ensures that Set-Content adds a newline at the end of the file if one isn't already there.
        Set-Content -Path $file.FullName -Value $newContentForFile -Encoding UTF8 -NoNewline:$false
    }
}

# Print summary
Write-Host "-------------------------------------"
if ($totalReplacements -gt 0) {
    Write-Host ("Script finished. Total replacements made: {0}" -f $totalReplacements) -ForegroundColor Green
} else {
    $noReplacementMsg = 'Script finished. No placeholder UUIDs matching the pattern `"{0}...`" were found to replace.' -f $placeholderPrefix
    Write-Host $noReplacementMsg -ForegroundColor Cyan
}
Write-Host "Please review the changes and rebuild your project."
