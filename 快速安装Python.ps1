# 快速安装 Python 3.11 的辅助脚本
# 注意：此脚本需要管理员权限，或手动下载安装

Write-Host "=== TrOCR 转换 - Python 3.11 安装辅助 ===" -ForegroundColor Green
Write-Host ""

Write-Host "检测到系统需要 Python 3.11 或 3.12 来转换 TrOCR 模型" -ForegroundColor Yellow
Write-Host ""

Write-Host "选项 1：手动下载安装（推荐）" -ForegroundColor Cyan
Write-Host "1. 访问: https://www.python.org/downloads/release/python-3119/" -ForegroundColor White
Write-Host "2. 下载 'Windows installer (64-bit)'" -ForegroundColor White
Write-Host "3. 运行安装程序，勾选 'Add Python to PATH'" -ForegroundColor White
Write-Host "4. 安装完成后重新运行转换脚本" -ForegroundColor White
Write-Host ""

Write-Host "选项 2：使用 winget 安装（如果可用）" -ForegroundColor Cyan
$hasWinget = Get-Command winget -ErrorAction SilentlyContinue
if ($hasWinget) {
    Write-Host "检测到 winget，可以运行:" -ForegroundColor Green
    Write-Host "  winget install Python.Python.3.11" -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "是否现在使用 winget 安装 Python 3.11? (y/n)"
    if ($response -eq "y" -or $response -eq "Y") {
        Write-Host "正在安装 Python 3.11..." -ForegroundColor Yellow
        winget install Python.Python.3.11
        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n✓ Python 3.11 安装成功!" -ForegroundColor Green
            Write-Host "请重新打开 PowerShell 窗口，然后运行转换脚本" -ForegroundColor Yellow
        } else {
            Write-Host "`n✗ 安装失败，请尝试手动安装" -ForegroundColor Red
        }
    }
} else {
    Write-Host "未检测到 winget，请使用选项 1 手动安装" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "安装完成后，运行以下命令验证:" -ForegroundColor Cyan
Write-Host "  python --version" -ForegroundColor White
Write-Host ""
Write-Host "然后创建虚拟环境并安装依赖:" -ForegroundColor Cyan
Write-Host "  python -m venv venv_trocr" -ForegroundColor White
Write-Host "  .\venv_trocr\Scripts\Activate.ps1" -ForegroundColor White
Write-Host "  pip install torch transformers tensorflow onnx onnx-tf" -ForegroundColor White
Write-Host "  python convert_trocr_to_tflite.py" -ForegroundColor White

