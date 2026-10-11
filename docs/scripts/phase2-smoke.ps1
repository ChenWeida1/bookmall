# 第二版冒烟：经网关走主路径与正反例关键项（用 curl.exe，避免 PS WebRequest 坑）。
$ErrorActionPreference = "Stop"
$base = $env:BOOKMALL_API_BASE
if (-not $base) { $base = "http://localhost:8088" }
$suffix = Get-Random -Maximum 999999
$user = "p2u$suffix"
$pass = "demo123456"

function Api {
  param(
    [Parameter(Mandatory)][string]$Method,
    [Parameter(Mandatory)][string]$Path,
    $Body = $null,
    $Token = $null
  )
  $bodyFile = [IO.Path]::GetTempFileName()
  $outFile = [IO.Path]::GetTempFileName()
  $args = @("-sS", "-X", $Method, "-o", $outFile, "-w", "%{http_code}", "$base$Path")
  if ($Token) { $args += @("-H", "Authorization: Bearer $Token") }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Compress -Depth 6
    [IO.File]::WriteAllText($bodyFile, $json, [Text.UTF8Encoding]::new($false))
    $args += @("-H", "Content-Type: application/json; charset=utf-8", "--data-binary", "@$bodyFile")
  }
  $codeLine = (& curl.exe @args).Trim()
  $bodyText = [IO.File]::ReadAllText($outFile, [Text.Encoding]::UTF8)
  Remove-Item $bodyFile, $outFile -ErrorAction SilentlyContinue
  if (-not $bodyText) { $bodyText = "{}" }
  return @{
    status = [int]$codeLine
    json = ($bodyText | ConvertFrom-Json)
  }
}

function Assert($cond, $msg) {
  if (-not $cond) { throw "ASSERT FAIL: $msg" }
  Write-Host "OK $msg"
}

Write-Host "BASE=$base"

$internal = Api -Method GET -Path "/internal/notifications"
Assert ($internal.status -eq 404) "gateway blocks /internal"

$reg = Api -Method POST -Path "/api/users/register" -Body @{ username = $user; password = $pass; nickname = "u$suffix" }
Assert ($reg.json.code -eq 0) "register"
$login = Api -Method POST -Path "/api/users/login" -Body @{ username = $user; password = $pass }
Assert ($login.json.code -eq 0) "login"
$token = $login.json.data.token
Assert ($login.json.data.user.role -eq "READER") "reader role"

$bad = Api -Method POST -Path "/api/users/login" -Body @{ username = $user; password = "wrongpass1" }
Assert ($bad.json.code -eq 40003) "bad password"

$c1 = Api -Method GET -Path "/api/categories"
Assert ($c1.json.code -eq 0 -and @($c1.json.data).Count -ge 1) "categories"
$b1 = Api -Method GET -Path "/api/books?page=1&size=10"
Assert ($b1.json.code -eq 0 -and @($b1.json.data.items).Count -ge 1) "books page"
$bookId = [int64]@($b1.json.data.items)[0].id

$add = Api -Method POST -Path "/api/cart/items" -Body @{ bookId = $bookId; quantity = 1 } -Token $token
Assert ($add.json.code -eq 0) "add cart"
$order = Api -Method POST -Path "/api/orders" -Body @{} -Token $token
Assert ($order.json.code -eq 0) "create order"
Assert ($order.json.data.status -eq "CREATED") "order CREATED"
$orderId = [int64]$order.json.data.id
$orderNo = [string]$order.json.data.orderNo

Start-Sleep -Seconds 2
$notes = Api -Method GET -Path "/api/notifications?page=1&size=20" -Token $token
Assert ($notes.json.code -eq 0) "notifications list"
$createdNote = @($notes.json.data.items) | Where-Object { $_.content -like "*$orderNo*" }
Assert ($null -ne $createdNote) "ORDER_CREATED notice visible"

$cancel = Api -Method POST -Path "/api/orders/$orderId/cancel" -Token $token
Assert ($cancel.json.code -eq 0 -and $cancel.json.data.status -eq "CANCELLED") "cancel ok"
$cancel2 = Api -Method POST -Path "/api/orders/$orderId/cancel" -Token $token
Assert ($cancel2.json.code -eq 40008) "cancel twice fails"

$user2 = "p2v$suffix"
Api -Method POST -Path "/api/users/register" -Body @{ username = $user2; password = $pass } | Out-Null
$login2 = Api -Method POST -Path "/api/users/login" -Body @{ username = $user2; password = $pass }
$token2 = $login2.json.data.token
$other = Api -Method POST -Path "/api/orders/$orderId/cancel" -Token $token2
Assert ($other.json.code -eq 40400) "cancel others 404"

$adminLogin = Api -Method POST -Path "/api/users/login" -Body @{ username = "admin"; password = "admin123456" }
Assert ($adminLogin.json.code -eq 0) "admin login"
Assert ($adminLogin.json.data.user.role -eq "ADMIN") "admin role"
$adminToken = $adminLogin.json.data.token
$off = Api -Method PUT -Path "/api/admin/books/$bookId" -Body @{ status = "OFF_SALE" } -Token $adminToken
Assert ($off.json.code -eq 0 -and $off.json.data.status -eq "OFF_SALE") "admin off sale"
$forbid = Api -Method PUT -Path "/api/admin/books/$bookId" -Body @{ status = "ON_SALE" } -Token $token
Assert ($forbid.json.code -eq 40300) "reader forbidden admin"
$on = Api -Method PUT -Path "/api/admin/books/$bookId" -Body @{ status = "ON_SALE" } -Token $adminToken
Assert ($on.json.code -eq 0 -and $on.json.data.status -eq "ON_SALE") "admin on sale"

$anon = Api -Method GET -Path "/api/cart"
Assert ($anon.json.code -eq 40100) "anon cart 401"

Write-Host "ALL SMOKE PASSED user=$user orderNo=$orderNo"
