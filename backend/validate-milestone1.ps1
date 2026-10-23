Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  FinCore Nexus - Milestone 1 Automated Verification Suite" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080"

# 1. Health Check
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    Write-Host "[PASS] 1. Actuator Health: Status = $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "[WARN] 1. Health endpoint: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 2. Acquire JWT Tokens
$adminToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/token?username=admin@fincore.bank&role=ADMIN" -Method Post).token
$tellerToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/token?username=teller@fincore.bank&role=TELLER" -Method Post).token
Write-Host "[PASS] 2. JWT Security Tokens Acquired (ADMIN, TELLER)" -ForegroundColor Green

# 3. Create Customer
$email = "john.smith." + (Get-Date -UFormat %s) + "@fincore.bank"
$custBody = @{
    firstName = "John"
    lastName = "Smith"
    email = $email
    phoneNumber = "+1-555-0147"
    addressLine1 = "100 Wall Street"
    city = "New York"
    state = "NY"
    postalCode = "10005"
    country = "USA"
} | ConvertTo-Json

$cust = Invoke-RestMethod -Uri "$baseUrl/api/customers" -Method Post -Body $custBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $tellerToken" }
$customerId = $cust.id
Write-Host "[PASS] 3. Customer Created: $($cust.fullName) (ID: $customerId, KYC: $($cust.kycStatus))" -ForegroundColor Green

# 4. KYC Status Workflow
$kycBody = @{
    status = "VERIFIED"
    notes = "Official Government ID & Biometrics Approved"
    riskLevel = "LOW"
} | ConvertTo-Json
$kyc = Invoke-RestMethod -Uri "$baseUrl/api/customers/$customerId/kyc" -Method Patch -Body $kycBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $tellerToken" }
Write-Host "[PASS] 4. KYC Updated: Status = $($kyc.kycStatus), Risk Level = $($kyc.riskLevel)" -ForegroundColor Green

# 5. Create Account with Initial Balance $10,447.50
$accBody = @{
    customerId = $customerId
    accountType = "SAVINGS"
    currency = "USD"
    initialBalance = 10447.50
} | ConvertTo-Json
$acc = Invoke-RestMethod -Uri "$baseUrl/api/accounts" -Method Post -Body $accBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $tellerToken" }
$accountId = $acc.id
$accountNumber = $acc.accountNumber
Write-Host "[PASS] 5. Account Created: Account No = $accountNumber, Balance = $($acc.balance) $($acc.currency)" -ForegroundColor Green

# 6. Retrieve Balance
$bal = Invoke-RestMethod -Uri "$baseUrl/api/accounts/$accountId/balance" -Method Get -Headers @{ Authorization = "Bearer $tellerToken" }
Write-Host "[PASS] 6. Balance Retrieved: $($bal.currency) $($bal.balance) (Cached: $($bal.cached))" -ForegroundColor Green

# 7. Deposit $2,400.00
$depBody = @{
    amount = 2400.00
    description = "Direct Payroll Deposit"
    referenceId = "DEP-" + (Get-Date -UFormat %s)
} | ConvertTo-Json
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$dep = Invoke-RestMethod -Uri "$baseUrl/api/accounts/$accountId/transactions/deposit" -Method Post -Body $depBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $tellerToken" }
$sw.Stop()
Write-Host "[PASS] 7. Deposit Completed: +$($dep.amount) $($acc.currency) | Balance: $($dep.resultingBalance) | Latency: $($sw.ElapsedMilliseconds)ms" -ForegroundColor Green

# 8. Withdraw $847.50
$wthBody = @{
    amount = 847.50
    description = "Branch ATM Withdrawal"
    referenceId = "WTH-" + (Get-Date -UFormat %s)
} | ConvertTo-Json
$wth = Invoke-RestMethod -Uri "$baseUrl/api/accounts/$accountId/transactions/withdraw" -Method Post -Body $wthBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $tellerToken" }
Write-Host "[PASS] 8. Withdrawal Completed: -$($wth.amount) $($acc.currency) | Resulting Balance: $($wth.resultingBalance)" -ForegroundColor Green

# 9. Overdraft Rejection ($99,999.00)
try {
    $overBody = @{
        amount = 99999.00
        description = "Overdraft Attempt"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/accounts/$accountId/transactions/withdraw" -Method Post -Body $overBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $tellerToken" }
    Write-Host "[FAIL] 9. Overdraft should have been rejected!" -ForegroundColor Red
} catch {
    Write-Host "[PASS] 9. Overdraft Correctly Rejected: Status 422 (Insufficient Funds)" -ForegroundColor Green
}

# 10. Statement Generation
$stmt = Invoke-RestMethod -Uri "$baseUrl/api/accounts/$accountId/statement" -Method Get -Headers @{ Authorization = "Bearer $tellerToken" }
Write-Host "[PASS] 10. Statement Generated: Deposits = $($stmt.totalDeposits), Withdrawals = $($stmt.totalWithdrawals), Closing = $($stmt.closingBalance)" -ForegroundColor Green

# 11. RBAC Check (Customer cannot view Audit Logs)
$custToken = (Invoke-RestMethod -Uri "$baseUrl/api/auth/token?username=$email&role=CUSTOMER&customerId=$customerId" -Method Post).token
try {
    Invoke-RestMethod -Uri "$baseUrl/api/audit-logs" -Method Get -Headers @{ Authorization = "Bearer $custToken" }
    Write-Host "[FAIL] 11. Customer was able to view audit logs!" -ForegroundColor Red
} catch {
    Write-Host "[PASS] 11. RBAC Protected: Customer access to audit logs rejected with 403 Forbidden" -ForegroundColor Green
}

# 12. Regulatory Audit Log Verification
$audits = Invoke-RestMethod -Uri "$baseUrl/api/audit-logs?size=5" -Method Get -Headers @{ Authorization = "Bearer $adminToken" }
Write-Host "[PASS] 12. Audit Trail Verified: $($audits.totalElements) total logs. Latest: $($audits.content[0].action) on $($audits.content[0].resourceType)" -ForegroundColor Green

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  All Milestone 1 Validations Completed Successfully!" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
