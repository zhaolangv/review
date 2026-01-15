# TrOCR 模型下载和转换脚本 (PowerShell)
# 注意: 此脚本主要用于下载相关资源，实际转换需要 Python 脚本

Write-Host "TrOCR 模型获取脚本" -ForegroundColor Green
Write-Host "=" * 60

# 检查 Python
Write-Host "`n检查 Python 环境..." -ForegroundColor Yellow
$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCmd) {
    Write-Host "未找到 Python，请先安装 Python 3.7+" -ForegroundColor Red
    Write-Host "下载地址: https://www.python.org/downloads/" -ForegroundColor Yellow
    exit 1
}

$pythonVersion = python --version 2>&1
Write-Host "找到 Python: $pythonVersion" -ForegroundColor Green

# 检查必要的 Python 包
Write-Host "`n检查 Python 依赖..." -ForegroundColor Yellow
$requiredPackages = @("torch", "transformers", "tensorflow", "onnx")

$missingPackages = @()
foreach ($package in $requiredPackages) {
    $result = python -c "import $package" 2>&1
    if ($LASTEXITCODE -ne 0) {
        $missingPackages += $package
    }
}

if ($missingPackages.Count -gt 0) {
    Write-Host "缺少以下依赖包: $($missingPackages -join ', ')" -ForegroundColor Red
    Write-Host "正在安装依赖包..." -ForegroundColor Yellow
    pip install $missingPackages
} else {
    Write-Host "所有依赖包已安装" -ForegroundColor Green
}

# 检查转换脚本是否存在
if (Test-Path "convert_trocr_to_tflite.py") {
    Write-Host "`n找到转换脚本: convert_trocr_to_tflite.py" -ForegroundColor Green
    Write-Host "`n运行转换脚本..." -ForegroundColor Yellow
    Write-Host "注意: 转换过程可能需要 10-30 分钟，请耐心等待" -ForegroundColor Yellow
    
    $response = Read-Host "是否开始转换? (y/n)"
    if ($response -eq "y" -or $response -eq "Y") {
        python convert_trocr_to_tflite.py
    } else {
        Write-Host "已取消转换" -ForegroundColor Yellow
    }
} else {
    Write-Host "`n未找到转换脚本: convert_trocr_to_tflite.py" -ForegroundColor Red
    Write-Host "请确保脚本文件存在于当前目录" -ForegroundColor Yellow
}

Write-Host "`n脚本执行完成" -ForegroundColor Green
Write-Host "`n提示:" -ForegroundColor Yellow
Write-Host "1. 如果转换成功，将生成 trocr_model.tflite 文件"
Write-Host "2. 将文件复制到: app/src/main/assets/trocr/model.tflite"
Write-Host "3. 重新编译应用"

