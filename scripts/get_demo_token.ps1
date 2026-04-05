param(
    [ValidateSet("customer", "driver", "ops")]
    [string]$Role = "customer",
    [string]$Password = "demo123",
    [string]$Realm = "routechain-demo",
    [string]$ClientId = "routechain-api",
    [string]$BaseUrl = "http://localhost:8088",
    [switch]$AsBearer
)

$ErrorActionPreference = "Stop"

$username = switch ($Role) {
    "customer" { "customer-demo" }
    "driver" { "driver-demo" }
    "ops" { "ops-demo" }
}

$tokenUrl = "$BaseUrl/realms/$Realm/protocol/openid-connect/token"
$body = @{
    grant_type = "password"
    client_id = $ClientId
    username = $username
    password = $Password
}

$response = Invoke-RestMethod -Method Post -Uri $tokenUrl -Body $body -ContentType "application/x-www-form-urlencoded"
$accessToken = $response.access_token

if ($AsBearer) {
    Write-Output "Bearer $accessToken"
} else {
    Write-Output $accessToken
}
