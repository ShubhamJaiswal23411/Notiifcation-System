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

Write-Host "`n=== 6. NOTIFICATIONS (send, dispatch, idempotency, scheduling) ===" -ForegroundColor Cyan

# Poll a notification until it reaches a terminal-ish status or times out
function Wait-ForStatus {
    param(
        [string]$NotificationId,
        [int]$TimeoutSeconds = 25,
        [int]$PollEverySeconds = 3
    )
    $elapsed = 0
    do {
        Start-Sleep -Seconds $PollEverySeconds
        $elapsed += $PollEverySeconds
        $current = Invoke-RestMethod -Uri "$BaseUrl/api/notifications/$NotificationId" -Method Get -Headers $tenantHeaders
        Write-Host "    [$elapsed s] status: $($current.status), attempts: $($current.attemptCount)"
    } while ($current.status -in @("QUEUED", "SENDING", "SCHEDULED") -and $elapsed -lt $TimeoutSeconds)
    return $current
}

$emailTemplateId = $template.id

$sent = Test-Step "Send immediate notification" {
    Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" `
        -Body "{`"channelType`":`"EMAIL`",`"templateId`":$emailTemplateId,`"recipient`":`"customer@example.com`",`"variables`":{`"name`":`"Alex`",`"orderId`":`"1001`",`"eta`":`"Friday`"}}"
}
Write-Host "  Notification ID: $($sent.id), initial status: $($sent.status)"

Write-Host "  Polling for dispatch result (up to 25s)..."
$final = Wait-ForStatus -NotificationId $sent.id
if ($final.status -in @("SENT", "FAILED", "DEAD_LETTER")) {
    Write-Host "[PASS] Immediate notification reached a dispatch outcome: $($final.status)" -ForegroundColor Green
    $Pass++
} else {
    Write-Host "[FAIL] Immediate notification never left $($final.status) within timeout - check sweeper/executor" -ForegroundColor Red
    $Fail++
}

$idempotencyKey = "idem-test-$Suffix"

$firstSend = Test-Step "Send with explicit idempotency key" {
    Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" `
        -Body "{`"channelType`":`"EMAIL`",`"templateId`":$emailTemplateId,`"recipient`":`"dup@example.com`",`"variables`":{`"name`":`"Sam`",`"orderId`":`"2002`",`"eta`":`"Monday`"},`"idempotencyKey`":`"$idempotencyKey`"}"
}

$secondSend = Test-Step "Re-send with SAME idempotency key" {
    Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" `
        -Body "{`"channelType`":`"EMAIL`",`"templateId`":$emailTemplateId,`"recipient`":`"dup@example.com`",`"variables`":{`"name`":`"Sam`",`"orderId`":`"2002`",`"eta`":`"Monday`"},`"idempotencyKey`":`"$idempotencyKey`"}"
}

if ($firstSend.id -eq $secondSend.id) {
    Write-Host "[PASS] Duplicate idempotency key returned the SAME notification (id $($firstSend.id)), no double-send" -ForegroundColor Green
    $Pass++
} else {
    Write-Host "[FAIL] Duplicate idempotency key created a NEW notification (ids $($firstSend.id) vs $($secondSend.id)) - idempotency broken" -ForegroundColor Red
    $Fail++
}

$scheduledTime = (Get-Date).ToUniversalTime().AddSeconds(35).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")

$scheduled = Test-Step "Send FUTURE-scheduled notification" {
    Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" `
        -Body "{`"channelType`":`"EMAIL`",`"templateId`":$emailTemplateId,`"recipient`":`"later@example.com`",`"variables`":{`"name`":`"Jordan`",`"orderId`":`"3003`",`"eta`":`"Tuesday`"},`"scheduledAt`":`"$scheduledTime`"}"
}

if ($scheduled.status -eq "SCHEDULED") {
    Write-Host "[PASS] Future-dated notification correctly created as SCHEDULED (not dispatched immediately)" -ForegroundColor Green
    $Pass++
} else {
    Write-Host "[FAIL] Expected status SCHEDULED, got $($scheduled.status)" -ForegroundColor Red
    $Fail++
}

Write-Host "  Waiting ~40s for the sweeper (runs every 30s) to pick up the scheduled send..."
$scheduledFinal = Wait-ForStatus -NotificationId $scheduled.id -TimeoutSeconds 45 -PollEverySeconds 5
if ($scheduledFinal.status -in @("SENT", "FAILED", "DEAD_LETTER")) {
    Write-Host "[PASS] Scheduled notification was picked up by the sweeper and dispatched: $($scheduledFinal.status)" -ForegroundColor Green
    $Pass++
} else {
    Write-Host "[FAIL] Scheduled notification still $($scheduledFinal.status) after timeout - sweeper may not be running" -ForegroundColor Red
    $Fail++
}

Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
Write-Host "Passed: $Pass" -ForegroundColor Green
Write-Host "Failed: $Fail" -ForegroundColor $(if ($Fail -eq 0) { "Green" } else { "Red" })
Write-Host "Tenant used this run: Acme-$Suffix (id $($tenant.id))"
