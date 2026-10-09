# Loads Backend/.env into the process environment, then starts the Spring Boot backend.
# Spring Boot does not read .env natively, so this script exports the vars first.
#
#   Usage (from the Backend folder):  ./run-backend.ps1
#
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $idx = $line.IndexOf("=")
            if ($idx -gt 0) {
                $name  = $line.Substring(0, $idx).Trim()
                $value = $line.Substring($idx + 1).Trim()
                Set-Item -Path "env:$name" -Value $value
            }
        }
    }
    Write-Host "Loaded environment from .env"
} else {
    Write-Host ".env not found at $envFile - using the existing environment only"
}

# -o = offline (deps already cached). Drop it if you need Maven to fetch anything.
& mvn -o spring-boot:run
