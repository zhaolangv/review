# TrOCR 转换 - Python 环境设置脚本
# 检查并设置 Python 3.11 或 3.12 环境

Write-Host "=== TrOCR 转换 - Python 环境检查 ===" -ForegroundColor Green
Write-Host ""

# 检查 Python 版本
$pythonVersions = @()
$pythonPaths = @()

# 查找所有 Python 可执行文件
$pythonCommands = Get-Command python*, py* -ErrorAction SilentlyContinue | Where-Object { 
    $_.Name -match '^python[\d.]*\.exe$' -or $_.Name -match '^py-\d+\.\d+\.exe$'
}

foreach ($cmd in $pythonCommands) {
    try {
        $versionOutput = & $cmd.Source --version 2>&1
        if ($versionOutput -match 'Python (\d+)\.(\d+)') {
            $major = [int]$matches[1]
            $minor = [int]$matches[2]
            $version = "$major.$minor"
            
            if (($major -eq 3 -and $minor -ge 11 -and $minor -le 12) -or ($major -eq 3 -and $minor -eq 14)) {
                $pythonVersions += @{
                    Version = $version
                    Path = $cmd.Source
                    Major = $major
                    Minor = $minor
                }
            }
        }
    } catch {
        # 忽略错误
    }
}

Write-Host "找到的 Python 版本:" -ForegroundColor Yellow
foreach ($py in $pythonVersions) {
    $status = if ($py.Major -eq 3 -and $py.Minor -ge 11 -and $py.Minor -le 12) { "推荐" } else { "可能不兼容" }
    $color = if ($py.Major -eq 3 -and $py.Minor -ge 11 -and $py.Minor -le 12) { "Green" } else { "Yellow" }
    Write-Host "  Python $($py.Version) - $($py.Path) - $status" -ForegroundColor $color
}

# 找到推荐的 Python 版本（3.11 或 3.12）
$recommendedPython = $pythonVersions | Where-Object { $_.Major -eq 3 -and $_.Minor -ge 11 -and $_.Minor -le 12 } | Select-Object -First 1

if ($recommendedPython) {
    Write-Host "`n✓ 找到兼容的 Python 版本: $($recommendedPython.Version)" -ForegroundColor Green
    Write-Host "路径: $($recommendedPython.Path)" -ForegroundColor Cyan
    
    # 创建虚拟环境
    $venvPath = "venv_trocr"
    if (Test-Path $venvPath) {
        Write-Host "`n虚拟环境已存在: $venvPath" -ForegroundColor Yellow
        $response = Read-Host "是否删除并重新创建? (y/n)"
        if ($response -eq "y" -or $response -eq "Y") {
            Remove-Item -Recurse -Force $venvPath
        } else {
            Write-Host "使用现有虚拟环境" -ForegroundColor Green
        }
    }
    
    if (-not (Test-Path $venvPath)) {
        Write-Host "`n正在创建虚拟环境..." -ForegroundColor Yellow
        & $recommendedPython.Path -m venv $venvPath
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ 虚拟环境创建成功" -ForegroundColor Green
        } else {
            Write-Host "✗ 虚拟环境创建失败" -ForegroundColor Red
            exit 1
        }
    }
    
    # 激活虚拟环境并安装依赖
    $activateScript = Join-Path $venvPath "Scripts\Activate.ps1"
    if (Test-Path $activateScript) {
        Write-Host "`n正在激活虚拟环境并安装依赖..." -ForegroundColor Yellow
        Write-Host "这可能需要几分钟时间，请耐心等待..." -ForegroundColor Yellow
        Write-Host ""
        
        # 使用虚拟环境中的 Python 安装依赖
        $venvPython = Join-Path $venvPath "Scripts\python.exe"
        & $venvPython -m pip install --upgrade pip
        & $venvPython -m pip install torch transformers tensorflow onnx onnx-tf
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n✓ 依赖安装成功!" -ForegroundColor Green
            Write-Host "`n下一步:" -ForegroundColor Yellow
            Write-Host "1. 激活虚拟环境: .\$venvPath\Scripts\Activate.ps1" -ForegroundColor Cyan
            Write-Host "2. 运行转换脚本: python convert_trocr_to_tflite.py" -ForegroundColor Cyan
            Write-Host "`n或者直接运行:" -ForegroundColor Yellow
            Write-Host "'$venvPython' convert_trocr_to_tflite.py" -ForegroundColor Cyan
        } else {
            Write-Host "`n✗ 依赖安装失败" -ForegroundColor Red
            exit 1
        }
    }
} else {
    Write-Host "`n⚠ 未找到 Python 3.11 或 3.12" -ForegroundColor Yellow
    Write-Host "`n请安装 Python 3.11 或 3.12:" -ForegroundColor Yellow
    Write-Host "1. 下载 Python 3.11: https://www.python.org/downloads/release/python-3119/" -ForegroundColor Cyan
    Write-Host "2. 或下载 Python 3.12: https://www.python.org/downloads/release/python-3124/" -ForegroundColor Cyan
    Write-Host "3. 安装时选择 'Add Python to PATH'" -ForegroundColor Cyan
    Write-Host "4. 重新运行此脚本" -ForegroundColor Cyan
}

