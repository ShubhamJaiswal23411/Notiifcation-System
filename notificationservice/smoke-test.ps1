# ============================================================
# Notification Service - Smoke Test Script
# Verifies: auth, tenant mgmt, RBAC, channels, templates
# Safe to re-run: uses a timestamp suffix so tenant/channel/
# template names never collide with previous runs.
# ============================================================

$BaseUrl = "http://localhost:8080"
$Suffix = Get-Date -Format "HHmmss"
$Pass = 0
$Fail = 0

function Test-Step {
    param(
        [string]$Name,
        [scriptblock]$Action,
        [switch]$ExpectFailure,
        [int]$ExpectedStatus = 0
    )
    try {
        $result = & $Action
        if ($ExpectFailure) {
            Write-Host "[FAIL] $Name - expected an error but request succeeded" -ForegroundColor Red
            $script:Fail++
        } else {
            Write-Host "[PASS] $Name" -ForegroundColor Green
            $script:Pass++
        }
        return $result
    } catch {
        $status = $null
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($ExpectFailure -and ($ExpectedStatus -eq 0 -or $status -eq $ExpectedStatus)) {
            Write-Host "[PASS] $Name (correctly failed with $status)" -ForegroundColor Green
            $script:Pass++
        } else {
            Write-Host "[FAIL] $Name - unexpected error (status: $status): $($_.Exception.Message)" -ForegroundColor Red
            $script:Fail++
        }
        return $null
    }
}

Write-Host "`n=== 1. AUTH ===" -ForegroundColor Cyan

$platformLogin = Test-Step "Platform admin login" {
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" `
        -Body '{"email":"admin@notify.local","password":"ChangeMe123!"}'
}
$platformHeaders = @{ Authorization = "Bearer $($platformLogin.token)" }

Test-Step "Login with wrong password fails (401)" -ExpectFailure -ExpectedStatus 401 {
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" `
        -Body '{"email":"admin@notify.local","password":"WrongPassword"}'
}

Test-Step "Request without token fails (401)" -ExpectFailure -ExpectedStatus 401 {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Get
}

Write-Host "`n=== 2. TENANT MANAGEMENT (platform admin only) ===" -ForegroundColor Cyan

$tenant = Test-Step "Create tenant" {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Post -Headers $platformHeaders `
        -ContentType "application/json" -Body "{`"name`":`"Acme-$Suffix`"}"
}
Write-Host "  Tenant ID: $($tenant.id), API Key: $($tenant.apiKey)"

Test-Step "List tenants" {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Get -Headers $platformHeaders
} | Out-Null

Test-Step "Get tenant by id" {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants/$($tenant.id)" -Method Get -Headers $platformHeaders
} | Out-Null

$tenantAdminEmail = "admin-$Suffix@acme.com"
$tenantAdminPassword = "AcmePass123!"

$tenantAdmin = Test-Step "Create tenant admin under new tenant" {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants/$($tenant.id)/admins" -Method Post -Headers $platformHeaders `
        -ContentType "application/json" -Body "{`"email`":`"$tenantAdminEmail`",`"password`":`"$tenantAdminPassword`"}"
}

Write-Host "`n=== 3. TENANT ADMIN AUTH + ROLE ENFORCEMENT ===" -ForegroundColor Cyan

$tenantLogin = Test-Step "Tenant admin login" {
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" `
        -Body "{`"email`":`"$tenantAdminEmail`",`"password`":`"$tenantAdminPassword`"}"
}
$tenantHeaders = @{ Authorization = "Bearer $($tenantLogin.token)" }
Write-Host "  Role in response: $($tenantLogin.role), tenantId: $($tenantLogin.tenantId)"

Test-Step "Tenant admin CANNOT create tenants (403)" -ExpectFailure -ExpectedStatus 403 {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"name":"Should Fail"}'
}

Write-Host "`n=== 4. CHANNELS (tenant-scoped) ===" -ForegroundColor Cyan

$channel = Test-Step "Create EMAIL channel" {
    Invoke-RestMethod -Uri "$BaseUrl/api/channels" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"channelType":"EMAIL","configJson":"{}"}'
}

Test-Step "Duplicate channel (same type) fails (409)" -ExpectFailure -ExpectedStatus 409 {
    Invoke-RestMethod -Uri "$BaseUrl/api/channels" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"channelType":"EMAIL","configJson":"{}"}'
}

Test-Step "Create SMS channel (different type, succeeds)" {
    Invoke-RestMethod -Uri "$BaseUrl/api/channels" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"channelType":"SMS","configJson":"{}"}'
} | Out-Null

Test-Step "List channels" {
    Invoke-RestMethod -Uri "$BaseUrl/api/channels" -Method Get -Headers $tenantHeaders
} | Out-Null

Write-Host "`n=== 5. TEMPLATES (tenant-scoped) ===" -ForegroundColor Cyan

$template = Test-Step "Create EMAIL template" {
    Invoke-RestMethod -Uri "$BaseUrl/api/templates" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"channelType":"EMAIL","name":"order_confirmation","subject":"Order confirmed!","body":"Hi {{name}}, order #{{orderId}} confirmed, arriving {{eta}}."}'
}

Test-Step "Duplicate template (same name+channel) fails (409)" -ExpectFailure -ExpectedStatus 409 {
    Invoke-RestMethod -Uri "$BaseUrl/api/templates" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"channelType":"EMAIL","name":"order_confirmation","subject":"Order confirmed!","body":"dup"}'
}

Test-Step "Same template name on SMS channel succeeds (different constraint scope)" {
    Invoke-RestMethod -Uri "$BaseUrl/api/templates" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"channelType":"SMS","name":"order_confirmation","subject":null,"body":"Order #{{orderId}} confirmed."}'
} | Out-Null

Test-Step "List templates" {
    Invoke-RestMethod -Uri "$BaseUrl/api/templates" -Method Get -Headers $tenantHeaders
} | Out-Null

Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
Write-Host "Passed: $Pass" -ForegroundColor Green
Write-Host "Failed: $Fail" -ForegroundColor $(if ($Fail -eq 0) { "Green" } else { "Red" })
Write-Host "Tenant used this run: Acme-$Suffix (id $($tenant.id))"