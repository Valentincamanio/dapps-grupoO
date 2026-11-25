<#
.SYNOPSIS
    Mide el tiempo de respuesta de GET /players/ranking con N consultas iguales (FR-010 de la 013).

.DESCRIPTION
    Hace -Warmup consultas que no se cuentan y después -Requests consultas medidas, todas con la
    misma -Query, con un único HttpClient de .NET (no Invoke-WebRequest, cuyo costo por pedido
    taparía el tiempo de un acierto del caché; research R7). Imprime:

    - cliente: N, promedio, p95 y máximo en ms, con un Stopwatch por consulta;
    - servidor: si existe el archivo de la clave de API de un ADMIN, lee /actuator/prometheus antes
      y después de las consultas medidas y calcula, sobre la diferencia de
      http_server_requests_seconds{uri="/players/ranking"}, la cantidad de pedidos, el promedio y el
      p95 interpolado de los buckets (la misma interpolación lineal de histogram_quantile);
    - caché: si existen las series cache_gets_total{cache="quotes-ranking"}, los aciertos, los
      fallos y el índice de aciertos. Si no existen (el caché anterior no publica métricas), "n/d".

    Al final imprime una tabla lista para copiar a specs/013-ranking-metricas-grafana/medicion-ranking.md.
    Nunca imprime la clave de API. Funciona con Windows PowerShell 5.1 y con PowerShell 7, sin
    dependencias nuevas.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File monitoring/measure-ranking.ps1 -Variant cache-anterior
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Query = 'page=0&size=20',
    [ValidateRange(1, 1000000)]
    [int]$Requests = 1000,
    [ValidateRange(0, 1000000)]
    [int]$Warmup = 20,
    [Parameter(Mandatory = $true)]
    [ValidateSet('cache-anterior', 'sin-cache', 'cache-nuevo')]
    [string]$Variant,
    [string]$ApiKeyFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ApiKeyFile)) {
    $ApiKeyFile = Join-Path $repoRoot 'monitoring/admin-api-key'
} elseif (-not [System.IO.Path]::IsPathRooted($ApiKeyFile)) {
    $ApiKeyFile = Join-Path $repoRoot $ApiKeyFile
}

$invariant = [System.Globalization.CultureInfo]::InvariantCulture
$rankingUri = '/players/ranking'
$rankingCache = 'quotes-ranking'

# Windows PowerShell 5.1 no carga System.Net.Http por defecto.
Add-Type -AssemblyName System.Net.Http

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)
$base = $BaseUrl.TrimEnd('/')
$rankingUrl = "$base$($rankingUri)?$Query"

function Format-Ms([double]$value) {
    return $value.ToString('0.000', $invariant)
}

# Hace un GET y devuelve el cuerpo; si la respuesta no es 2xx, termina con un mensaje en español.
function Invoke-Get([string]$url, [hashtable]$headers) {
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, $url)
    if ($null -ne $headers) {
        foreach ($name in $headers.Keys) {
            [void]$request.Headers.TryAddWithoutValidation($name, [string]$headers[$name])
        }
    }
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
    } catch {
        throw "No se pudo conectar con $url. ¿Está levantado el backend? ($($_.Exception.Message))"
    }
    try {
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "La consulta a $url respondió $([int]$response.StatusCode). Se corta la medición."
        }
        return $body
    } finally {
        $response.Dispose()
        $request.Dispose()
    }
}

# Lee el valor numérico de una línea del formato de texto de Prometheus.
function Get-SampleValue([string]$line) {
    $raw = $line.Substring($line.LastIndexOf(' ') + 1)
    if ($raw -eq '+Inf') { return [double]::PositiveInfinity }
    return [double]::Parse($raw, $invariant)
}

# Junta, de una lectura de /actuator/prometheus, los contadores del histograma del ranking (sumando
# todas las series de status y outcome) y los aciertos y fallos del caché del ranking.
function Read-Snapshot([string]$text) {
    $snapshot = @{
        Count   = 0.0
        Sum     = 0.0
        Buckets = @{}
        Hit     = $null
        Miss    = $null
    }
    $uriLabel = 'uri="' + $rankingUri + '"'
    $cacheLabel = 'cache="' + $rankingCache + '"'
    foreach ($line in ($text -split "`n")) {
        $line = $line.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) { continue }
        if ($line.StartsWith('http_server_requests_seconds_') -and $line.Contains($uriLabel)) {
            if ($line.StartsWith('http_server_requests_seconds_count{')) {
                $snapshot.Count += Get-SampleValue $line
            } elseif ($line.StartsWith('http_server_requests_seconds_sum{')) {
                $snapshot.Sum += Get-SampleValue $line
            } elseif ($line.StartsWith('http_server_requests_seconds_bucket{')) {
                if ($line -match 'le="([^"]+)"') {
                    $le = $Matches[1]
                    if (-not $snapshot.Buckets.ContainsKey($le)) { $snapshot.Buckets[$le] = 0.0 }
                    $snapshot.Buckets[$le] += Get-SampleValue $line
                }
            }
        } elseif ($line.StartsWith('cache_gets_total{') -and $line.Contains($cacheLabel)) {
            if ($line.Contains('result="hit"')) {
                if ($null -eq $snapshot.Hit) { $snapshot.Hit = 0.0 }
                $snapshot.Hit += Get-SampleValue $line
            } elseif ($line.Contains('result="miss"')) {
                if ($null -eq $snapshot.Miss) { $snapshot.Miss = 0.0 }
                $snapshot.Miss += Get-SampleValue $line
            }
        }
    }
    return $snapshot
}

function Read-Metrics([string]$apiKey) {
    $text = Invoke-Get "$base/actuator/prometheus" @{ 'X-API-Key' = $apiKey; 'Accept' = 'text/plain;version=0.0.4' }
    return Read-Snapshot $text
}

# El p95 de la diferencia de buckets acumulados, con la interpolación lineal de histogram_quantile.
function Get-BucketQuantile([double]$quantile, [hashtable]$before, [hashtable]$after) {
    $bounds = @()
    foreach ($le in $after.Keys) {
        $upper = if ($le -eq '+Inf') { [double]::PositiveInfinity } else { [double]::Parse($le, $invariant) }
        $previous = if ($before.ContainsKey($le)) { $before[$le] } else { 0.0 }
        $bounds += [pscustomobject]@{ Upper = $upper; Count = ($after[$le] - $previous) }
    }
    $bounds = @($bounds | Sort-Object Upper)
    if ($bounds.Count -eq 0) { return $null }
    $total = $bounds[$bounds.Count - 1].Count
    if ($total -le 0) { return $null }
    $rank = $quantile * $total
    $lowerBound = 0.0
    $lowerCount = 0.0
    for ($i = 0; $i -lt $bounds.Count; $i++) {
        $bucket = $bounds[$i]
        if ($bucket.Count -ge $rank) {
            if ([double]::IsPositiveInfinity($bucket.Upper)) {
                return $lowerBound
            }
            $width = $bucket.Count - $lowerCount
            if ($width -le 0) { return $bucket.Upper }
            return $lowerBound + ($bucket.Upper - $lowerBound) * (($rank - $lowerCount) / $width)
        }
        $lowerBound = $bucket.Upper
        $lowerCount = $bucket.Count
    }
    return $lowerBound
}

try {
    $apiKey = $null
    if (Test-Path -LiteralPath $ApiKeyFile -PathType Leaf) {
        $apiKey = (Get-Content -LiteralPath $ApiKeyFile -Raw).Trim()
        if ($apiKey.Length -eq 0) { $apiKey = $null }
    }
    if ($null -eq $apiKey) {
        Write-Host "No hay clave de API en $ApiKeyFile`: se miden solo los tiempos del cliente."
    }

    Write-Host "Variante: $Variant. Consulta: GET $rankingUri`?$Query. Calentamiento: $Warmup. Consultas medidas: $Requests."

    for ($i = 0; $i -lt $Warmup; $i++) {
        [void](Invoke-Get $rankingUrl $null)
    }

    $before = $null
    if ($null -ne $apiKey) { $before = Read-Metrics $apiKey }

    $times = New-Object 'double[]' $Requests
    $stopwatch = New-Object System.Diagnostics.Stopwatch
    for ($i = 0; $i -lt $Requests; $i++) {
        $stopwatch.Restart()
        [void](Invoke-Get $rankingUrl $null)
        $stopwatch.Stop()
        $times[$i] = $stopwatch.Elapsed.TotalMilliseconds
    }

    $after = $null
    if ($null -ne $apiKey) { $after = Read-Metrics $apiKey }

    # Cliente: promedio, p95 por orden (índice ceil(0.95 * N) - 1) y máximo.
    $sorted = [double[]]$times.Clone()
    [Array]::Sort($sorted)
    $clientAvg = ($times | Measure-Object -Average).Average
    $clientP95 = $sorted[[int][Math]::Ceiling(0.95 * $Requests) - 1]
    $clientMax = $sorted[$Requests - 1]

    $serverCount = 'n/d'
    $serverAvg = 'n/d'
    $serverP95 = 'n/d'
    $hits = 'n/d'
    $misses = 'n/d'
    $hitRatio = 'n/d'
    if ($null -ne $after) {
        $deltaCount = $after.Count - $before.Count
        $deltaSum = $after.Sum - $before.Sum
        $serverCount = [string][int64]$deltaCount
        if ($deltaCount -gt 0) {
            $serverAvg = Format-Ms (1000.0 * $deltaSum / $deltaCount)
            $p95 = Get-BucketQuantile 0.95 $before.Buckets $after.Buckets
            if ($null -ne $p95) { $serverP95 = Format-Ms (1000.0 * $p95) }
        }
        if ($null -ne $after.Hit -and $null -ne $after.Miss) {
            $beforeHit = if ($null -ne $before.Hit) { $before.Hit } else { 0.0 }
            $beforeMiss = if ($null -ne $before.Miss) { $before.Miss } else { 0.0 }
            $deltaHit = $after.Hit - $beforeHit
            $deltaMiss = $after.Miss - $beforeMiss
            $hits = [string][int64]$deltaHit
            $misses = [string][int64]$deltaMiss
            if (($deltaHit + $deltaMiss) -gt 0) {
                $hitRatio = (100.0 * $deltaHit / ($deltaHit + $deltaMiss)).ToString('0.00', $invariant) + ' %'
            }
        }
    }

    $date = (Get-Date).ToString('yyyy-MM-dd HH:mm')
    Write-Host ''
    Write-Host 'Cliente (ms)'
    Write-Host "  N: $Requests | promedio: $(Format-Ms $clientAvg) | p95: $(Format-Ms $clientP95) | máximo: $(Format-Ms $clientMax)"
    Write-Host 'Servidor, http_server_requests_seconds (ms)'
    Write-Host "  pedidos: $serverCount | promedio: $serverAvg | p95 (buckets): $serverP95"
    Write-Host "Caché $rankingCache"
    Write-Host "  aciertos: $hits | fallos: $misses | índice de aciertos: $hitRatio"
    Write-Host ''
    Write-Host '| Variante | Fecha | N | Promedio cliente (ms) | p95 cliente (ms) | Máximo cliente (ms) | Pedidos servidor | Promedio servidor (ms) | p95 servidor (ms) | Aciertos | Fallos | Índice de aciertos |'
    Write-Host '|---|---|---|---|---|---|---|---|---|---|---|---|'
    Write-Host "| $Variant | $date | $Requests | $(Format-Ms $clientAvg) | $(Format-Ms $clientP95) | $(Format-Ms $clientMax) | $serverCount | $serverAvg | $serverP95 | $hits | $misses | $hitRatio |"
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    $client.Dispose()
}
