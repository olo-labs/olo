# Copyright (c) 2026 Olo Labs
# SPDX-License-Identifier: Apache-2.0
#
# Seeds sample <tenantId>:olo:kernel:config:<queueName> keys so Chat Queue / Pipeline dropdowns populate.
# Requires redis-cli (Redis for Windows, or WSL, or Docker: docker run --rm -it redis redis-cli ...)
#
# Usage:
#   .\scripts\seed-kernel-config-redis.ps1
# Optional: $env:OLO_DEFAULT_TENANT_ID, $env:OLO_CACHE_HOST, $env:OLO_CACHE_PORT

$ErrorActionPreference = "Stop"

if (-not (Get-Command redis-cli -ErrorAction SilentlyContinue)) {
    Write-Error "redis-cli not on PATH. Install Redis tools or run: docker run --rm -e REDIS_HOST=host.docker.internal redis:alpine redis-cli -h host.docker.internal -p 46379 PING"
    exit 1
}

$tenant = $env:OLO_DEFAULT_TENANT_ID
if (-not $tenant) { $tenant = "default" }

$redisHost = $env:OLO_CACHE_HOST
if (-not $redisHost) { $redisHost = "127.0.0.1" }

$redisPort = $env:OLO_CACHE_PORT
if (-not $redisPort) { $redisPort = "46379" }

$q1 = "olo-chat-queue:1.0"
$json1 = '{"version":"1.0","pipelines":{"default":{"name":"Default chat"},"research":{"name":"Research"},"rag":{"name":"RAG"}}}'
$q2 = "olo-rag-queue:1.0"
$json2 = '{"version":"1.0","pipelines":["default","ingest"]}'

$key1 = "${tenant}:olo:kernel:config:${q1}"
$key2 = "${tenant}:olo:kernel:config:${q2}"

function Redis-Set {
    param([string]$Key, [string]$Json)
    $Json | & redis-cli -h $redisHost -p $redisPort -x SET $Key
}

Write-Host "SET $key1"
Redis-Set -Key $key1 -Json $json1
Write-Host "SET $key2"
Redis-Set -Key $key2 -Json $json2
Write-Host "Done. Refresh the Chat UI (Queue / Pipeline)."
