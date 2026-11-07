# 启动 reapnow 微服务版（Nacos + 网关 + 4 个业务服务）
# 前置：Nacos 需已在 8848 运行（_tools\cloud\nacos\bin\startup.cmd -m standalone）
param([switch]$SkipNacosCheck)

$root = $PSScriptRoot
# 加载本地环境变量（数据库密码等敏感配置不进仓库）
$envFile = Join-Path $PSScriptRoot "env.local.ps1"
if (Test-Path $envFile) { . $envFile }
$log  = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $log | Out-Null

function Test-Port([int]$port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect("127.0.0.1", $port); $c.Close(); return $true
    } catch { return $false }
}

# 0) SkyWalking Java Agent（存在就挂载，不存在则自动跳过）
$apmAgent = "D:\skywalking\agent\skywalking-agent\skywalking-agent.jar"
if (Test-Path $apmAgent) {
    Write-Host "SkyWalking Agent: 已启用" -ForegroundColor Green
} else {
    $apmAgent = $null
    Write-Host "SkyWalking Agent: 未找到，跳过（链路追踪不可用）" -ForegroundColor DarkYellow
}

# 1) 前置检查：Nacos
if (-not (Test-Port 8848)) {
    Write-Host "Nacos 未在 8848 运行！" -ForegroundColor Red
    Write-Host "请先执行: _tools\cloud\nacos\bin\startup.cmd -m standalone" -ForegroundColor Yellow
    if (-not $SkipNacosCheck) { exit 1 }
} else {
    Write-Host "Nacos 8848 正常" -ForegroundColor Green
}

# 2) 只停 reapnow 服务进程，不动 Nacos
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*reapnow-*service*' -or $_.CommandLine -like '*reapnow-gateway*' } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
        Write-Host "停止 PID=$($_.ProcessId)" -ForegroundColor DarkYellow
    }
Start-Sleep -Seconds 2

# 3) 按依赖顺序启动：业务服务 → 网关
$services = @(
    @{ name = "user-service";    jar = "reapnow-user-service\target\reapnow-user-service-0.1.0-SNAPSHOT.jar";       port = 8081 },
    @{ name = "demand-service";  jar = "reapnow-demand-service\target\reapnow-demand-service-0.1.0-SNAPSHOT.jar";   port = 8082 },
    @{ name = "order-service";   jar = "reapnow-order-service\target\reapnow-order-service-0.1.0-SNAPSHOT.jar";     port = 8083 },
    @{ name = "payment-service"; jar = "reapnow-payment-service\target\reapnow-payment-service-0.1.0-SNAPSHOT.jar"; port = 8084 },
    @{ name = "gateway";         jar = "reapnow-gateway\target\reapnow-gateway-0.1.0-SNAPSHOT.jar";                 port = 8080 }
)

foreach ($s in $services) {
    $jarPath = Join-Path $root $s.jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "缺少 $($s.jar)，请先执行 mvn install" -ForegroundColor Red
        exit 1
    }
    $javaArgs = @()
    # ---- JVM 堆上限 ----
    # 不加 -Xmx 时 JVM 默认最大堆 = 物理内存的 1/4（16G 机器约 3.9G），
    # 5 个服务加起来会把内存吃光，导致 SkyWalking OAP 被系统干掉。
    # 这套业务的实际堆需求很小，256M 足够；留出空间给 Nacos/Seata/SkyWalking/浏览器。
    $javaArgs += @("-Xms128m", "-Xmx256m", "-XX:+UseSerialGC")
    if ($apmAgent) {
        $javaArgs += "-javaagent:$apmAgent"
        $javaArgs += "-Dskywalking.agent.service_name=$($s.name)"
        $javaArgs += "-Dskywalking.collector.backend_service=127.0.0.1:11800"
    }
    $javaArgs += @("-jar", $jarPath)

    Start-Process -FilePath "java" -ArgumentList $javaArgs `
        -WorkingDirectory $root -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $log "$($s.name).log") `
        -RedirectStandardError  (Join-Path $log "$($s.name).err")

    Write-Host ("启动 {0,-16} 端口 {1} ..." -f $s.name, $s.port) -NoNewline
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 1200
        if (Test-Port $s.port) { $ok = $true; break }
    }
    if ($ok) { Write-Host " OK" -ForegroundColor Green }
    else { Write-Host " 超时，看 logs\$($s.name).err" -ForegroundColor Red }
}

Write-Host ""
Write-Host "=== 端口状态 ===" -ForegroundColor Cyan
foreach ($s in $services) {
    $state = if (Test-Port $s.port) { "监听中" } else { "未监听" }
    Write-Host ("  {0,-16} :{1}  {2}" -f $s.name, $s.port, $state)
}
Write-Host ("  {0,-16} :{1}  {2}" -f "nacos", 8848, $(if (Test-Port 8848) { "监听中" } else { "未监听" }))
Write-Host ""
Write-Host "网关入口: http://localhost:8080    前端: http://localhost:5174" -ForegroundColor Cyan
Write-Host "Nacos 控制台: http://localhost:8848/nacos" -ForegroundColor Cyan
Write-Host "日志目录: $log" -ForegroundColor DarkGray
