# ============================================================
# Multi-Tenant Notification Service - LIVE DEMO SCRIPT
# ============================================================
# Run this during the recording instead of typing commands live.
# It pauses before each section ("Press Enter to continue...") so
# you can talk first, then hit Enter to run that section and let
# the output appear on screen while you narrate over it.
#
# Prerequisite: app must already be running (./gradlew bootRun)
# with a FRESH restart so there's no leftover data from earlier
# testing sessions.
# ============================================================

$BaseUrl = "http://localhost:8080"

function Section {
    param([string]$Title)
    Write-Host "`n============================================================" -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
}

function Pause-ForNarration {
    param([string]$Prompt = "Press Enter to continue...")
    Write-Host "`n>>> $Prompt" -ForegroundColor Yellow
    Read-Host | Out-Null
}

function Call {
    param([string]$Description)
    Write-Host "`n--- $Description ---" -ForegroundColor DarkGray
}

Write-Host "DEMO READY. Press Enter whenever you want to run the next section." -ForegroundColor Green
Pause-ForNarration "Press Enter to start Section 1: Platform admin + tenant setup"

# ============================================================
Section "1. PLATFORM ADMIN LOGIN + TENANT CREATION"
# ============================================================

Call "POST /api/auth/login  (platform admin)"
$platformLogin = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"email":"admin@notify.local","password":"ChangeMe123!"}'
$platformHeaders = @{ Authorization = "Bearer $($platformLogin.token)" }
Write-Host "Expected: a JWT token, role PLATFORM_ADMIN" -ForegroundColor DarkGray
Write-Host "Role: $($platformLogin.role)"

Call "POST /api/tenants  (create a new tenant)"
$tenant = Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Post -Headers $platformHeaders `
    -ContentType "application/json" -Body '{"name":"Acme Corp"}'
Write-Host "Expected: 201, tenant with an auto-generated API key" -ForegroundColor DarkGray
$tenant | Format-List

Call "POST /api/tenants/{id}/admins  (provision a tenant admin)"
$tenantAdmin = Invoke-RestMethod -Uri "$BaseUrl/api/tenants/$($tenant.id)/admins" -Method Post -Headers $platformHeaders `
    -ContentType "application/json" -Body '{"email":"admin@acme.com","password":"AcmePass123!"}'
Write-Host "Expected: 201, tenant admin linked to tenant $($tenant.id)" -ForegroundColor DarkGray
$tenantAdmin | Format-List

Pause-ForNarration "Press Enter to start Section 2: Tenant admin login + RBAC proof"

# ============================================================
Section "2. TENANT ADMIN LOGIN + RBAC ENFORCEMENT"
# ============================================================

Call "POST /api/auth/login  (tenant admin)"
$tenantLogin = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"email":"admin@acme.com","password":"AcmePass123!"}'
$tenantHeaders = @{ Authorization = "Bearer $($tenantLogin.token)" }
Write-Host "Expected: role TENANT_ADMIN, tenantId embedded in the token claims" -ForegroundColor DarkGray
Write-Host "Role: $($tenantLogin.role)   TenantId: $($tenantLogin.tenantId)"

Call "POST /api/tenants  AS TENANT ADMIN  (should be blocked)"
Write-Host "Expected: 403 Forbidden - valid token, wrong role" -ForegroundColor DarkGray
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Post -Headers $tenantHeaders `
        -ContentType "application/json" -Body '{"name":"Should Fail"}'
} catch {
    Write-Host "Got: $($_.Exception.Response.StatusCode) - correctly blocked" -ForegroundColor Green
}

Call "GET /api/tenants  WITH NO TOKEN AT ALL"
Write-Host "Expected: 401 Unauthorized - no credentials, distinct from the 403 above" -ForegroundColor DarkGray
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/tenants" -Method Get
} catch {
    Write-Host "Got: $($_.Exception.Response.StatusCode) - correctly rejected" -ForegroundColor Green
}

Pause-ForNarration "Press Enter to start Section 3: Channel + template setup"

# ============================================================
Section "3. CHANNEL AND TEMPLATE SETUP"
# ============================================================

Call "POST /api/channels  (EMAIL channel for this tenant)"
$channel = Invoke-RestMethod -Uri "$BaseUrl/api/channels" -Method Post -Headers $tenantHeaders `
    -ContentType "application/json" -Body '{"channelType":"EMAIL","configJson":"{}"}'
Write-Host "Expected: 201, channel created for tenant $($tenant.id)" -ForegroundColor DarkGray
$channel | Format-List

Call "POST /api/templates  (with {{variable}} placeholders)"
$template = Invoke-RestMethod -Uri "$BaseUrl/api/templates" -Method Post -Headers $tenantHeaders `
    -ContentType "application/json" `
    -Body '{"channelType":"EMAIL","name":"order_confirmation","subject":"Order confirmed!","body":"Hi {{name}}, order #{{orderId}} confirmed, arriving {{eta}}."}'
Write-Host "Expected: 201, note the {{name}} {{orderId}} {{eta}} placeholders in body" -ForegroundColor DarkGray
$template | Format-List

Pause-ForNarration "Press Enter to start Section 4: Send a notification and watch it dispatch"

# ============================================================
Section "4. SEND A NOTIFICATION AND WATCH IT DISPATCH"
# ============================================================

Call "POST /api/notifications  (immediate send)"
$body = '{"channelType":"EMAIL","templateId":' + $template.id + ',"recipient":"customer@example.com","variables":{"name":"Alex","orderId":"1001","eta":"Friday"}}'
$notif = Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders `
    -ContentType "application/json" -Body $body
Write-Host "Expected: 201, initial status QUEUED (dispatch happens asynchronously)" -ForegroundColor DarkGray
Write-Host "Notification ID: $($notif.id)   Initial status: $($notif.status)"

Write-Host "`n>>> Switch to the app console window to see the dispatch log line <<<" -ForegroundColor Yellow
Write-Host "Waiting 4 seconds for the async dispatcher to process it..." -ForegroundColor DarkGray
Start-Sleep -Seconds 4

Call "GET /api/notifications/{id}  (check the outcome)"
$final = Invoke-RestMethod -Uri "$BaseUrl/api/notifications/$($notif.id)" -Method Get -Headers $tenantHeaders
Write-Host "Expected: SENT (or FAILED - the mock provider simulates ~15% failure, that's fine, narrate it as the retry path)" -ForegroundColor DarkGray
Write-Host "Status: $($final.status)   Attempts: $($final.attemptCount)"

Call "GET /api/notifications/{id}/attempts  (the audit trail)"
$attempts = Invoke-RestMethod -Uri "$BaseUrl/api/notifications/$($notif.id)/attempts" -Method Get -Headers $tenantHeaders
Write-Host "Expected: one row per delivery attempt, with outcome and timestamp" -ForegroundColor DarkGray
$attempts | Format-Table

Pause-ForNarration "Press Enter to start Section 5: Idempotency proof"

# ============================================================
Section "5. IDEMPOTENCY - SAME KEY, NO DUPLICATE SEND"
# ============================================================

Call "POST /api/notifications  TWICE with the SAME idempotencyKey"
$idemKey = "demo-idem-key-001"
$dupBody = '{"channelType":"EMAIL","templateId":' + $template.id + ',"recipient":"dup@example.com","variables":{"name":"Sam","orderId":"2002","eta":"Monday"},"idempotencyKey":"' + $idemKey + '"}'

$first = Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders -ContentType "application/json" -Body $dupBody
$second = Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders -ContentType "application/json" -Body $dupBody

Write-Host "Expected: BOTH calls return the SAME notification ID - no duplicate created" -ForegroundColor DarkGray
Write-Host "First call ID:  $($first.id)"
Write-Host "Second call ID: $($second.id)"
if ($first.id -eq $second.id) {
    Write-Host "MATCH - idempotency working correctly" -ForegroundColor Green
} else {
    Write-Host "MISMATCH - unexpected, investigate" -ForegroundColor Red
}

Pause-ForNarration "Press Enter to start Section 6 (OPTIONAL): scheduled send + sweeper - has a ~35s wait, skip if short on time"

# ============================================================
Section "6. SCHEDULED SEND + BACKGROUND SWEEPER (has a real wait)"
# ============================================================

Call "POST /api/notifications  (scheduledAt 35 seconds in the future)"
$scheduledTime = (Get-Date).ToUniversalTime().AddSeconds(35).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$schedBody = '{"channelType":"EMAIL","templateId":' + $template.id + ',"recipient":"later@example.com","variables":{"name":"Jordan","orderId":"3003","eta":"Tuesday"},"scheduledAt":"' + $scheduledTime + '"}'
$scheduled = Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Method Post -Headers $tenantHeaders -ContentType "application/json" -Body $schedBody
Write-Host "Expected: status SCHEDULED, NOT dispatched immediately" -ForegroundColor DarkGray
Write-Host "Status: $($scheduled.status)"

Write-Host "`n>>> Narrate over this wait: the sweeper runs every 30 seconds and will pick this up <<<" -ForegroundColor Yellow
Start-Sleep -Seconds 40

Call "GET /api/notifications/{id}  (after the wait)"
$scheduledFinal = Invoke-RestMethod -Uri "$BaseUrl/api/notifications/$($scheduled.id)" -Method Get -Headers $tenantHeaders
Write-Host "Expected: SENT or FAILED - no longer SCHEDULED, the sweeper picked it up automatically" -ForegroundColor DarkGray
Write-Host "Status: $($scheduledFinal.status)"

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host " DEMO COMPLETE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Remaining to show separately: ./gradlew test jacocoTestReport, then open" -ForegroundColor DarkGray
Write-Host "build/reports/jacoco/test/html/index.html for the coverage report." -ForegroundColor DarkGray
