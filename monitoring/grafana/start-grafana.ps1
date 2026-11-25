<#
.SYNOPSIS
    Levanta Grafana OSS (zip de Windows, 11.0 o posterior) con el data source y el dashboard del repo.

.DESCRIPTION
    Pone, solo para el proceso de Grafana, las rutas de provisioning, datos, logs y plugins dentro
    de monitoring/grafana/ (research R13 de la 013), y la carpeta de dashboards que lee el provider.
    Si existe monitoring/grafana/admin-credentials (ignorado por git), aplica su usuario y su
    contraseña de administrador; la contraseña nunca se imprime (research R14). Grafana la aplica
    solo cuando crea su base: en el primer arranque o después de borrar monitoring/grafana/data/.

    Al terminar, las variables de entorno del proceso actual vuelven a como estaban.

.PARAMETER GrafanaHome
    La carpeta donde se descomprimió el zip de Grafana. Si no se pasa, se usa $env:GRAFANA_HOME.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File monitoring/grafana/start-grafana.ps1 -GrafanaHome C:\grafana
#>
param(
    [string]$GrafanaHome = $env:GRAFANA_HOME
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

if ([string]::IsNullOrWhiteSpace($GrafanaHome)) {
    Write-Host 'Falta la carpeta de Grafana: pasala con -GrafanaHome o definí la variable GRAFANA_HOME.' -ForegroundColor Red
    exit 1
}
$GrafanaHome = $GrafanaHome.TrimEnd('\', '/')
$grafanaExe = Join-Path $GrafanaHome 'bin\grafana.exe'
if (-not (Test-Path -LiteralPath $grafanaExe -PathType Leaf)) {
    Write-Host "No se encontró $grafanaExe. ¿Es la carpeta del zip de Grafana OSS 11.0 o posterior?" -ForegroundColor Red
    exit 1
}

# Las rutas absolutas del repo, calculadas desde este script (monitoring/grafana/).
$grafanaDir = $PSScriptRoot
$dataDir = Join-Path $grafanaDir 'data'
$paths = [ordered]@{
    'GF_PATHS_PROVISIONING'           = (Join-Path $grafanaDir 'provisioning')
    'GF_PATHS_DATA'                   = $dataDir
    'GF_PATHS_LOGS'                   = (Join-Path $dataDir 'log')
    'GF_PATHS_PLUGINS'                = (Join-Path $dataDir 'plugins')
    'FUTBOLMARKET_GRAFANA_DASHBOARDS' = (Join-Path $grafanaDir 'dashboards')
}
if (-not (Test-Path -LiteralPath $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir | Out-Null
}

# La credencial local, en pares clave=valor. Se ignoran las líneas vacías y los comentarios, y una
# clave vacía no se aplica.
$credentials = @{}
$credentialsFile = Join-Path $grafanaDir 'admin-credentials'
if (Test-Path -LiteralPath $credentialsFile -PathType Leaf) {
    foreach ($line in Get-Content -LiteralPath $credentialsFile) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($value.Length -eq 0) { continue }
        if ($key -eq 'admin_user') { $credentials['GF_SECURITY_ADMIN_USER'] = $value }
        elseif ($key -eq 'admin_password') { $credentials['GF_SECURITY_ADMIN_PASSWORD'] = $value }
    }
}

$variables = [ordered]@{}
foreach ($name in $paths.Keys) { $variables[$name] = $paths[$name] }
foreach ($name in $credentials.Keys) { $variables[$name] = $credentials[$name] }

# Se guardan los valores previos para restaurarlos al terminar.
$previous = @{}
foreach ($name in $variables.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    foreach ($name in $variables.Keys) {
        [Environment]::SetEnvironmentVariable($name, [string]$variables[$name], 'Process')
    }
    if ($credentials.ContainsKey('GF_SECURITY_ADMIN_PASSWORD')) {
        Write-Host 'Se aplicó la credencial local de monitoring/grafana/admin-credentials (solo vale al crear la base).'
    } else {
        Write-Host 'Sin credencial local: el administrador es admin/admin y Grafana pide cambiarla al entrar.'
    }
    Write-Host "Provisioning: $($paths['GF_PATHS_PROVISIONING'])"
    Write-Host "Datos y logs: $dataDir"
    Write-Host 'Dashboard: http://localhost:3000/d/futbol-market (Ctrl+C para detener Grafana)'
    & $grafanaExe server --homepath $GrafanaHome
    exit $LASTEXITCODE
} finally {
    foreach ($name in $variables.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
