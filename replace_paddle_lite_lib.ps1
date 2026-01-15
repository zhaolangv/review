# Paddle Lite 库文件替换脚本
# 将 tiny_publish 版本替换为 with_extra 版本

$ErrorActionPreference = "Stop"

Write-Host "`n🔧 Paddle Lite 库文件替换工具" -ForegroundColor Cyan
Write-Host "=" * 50 -ForegroundColor Gray

# 目标文件路径
$targetFile = "paddle_lite_libs_v2_10\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so"
$targetDir = Split-Path $targetFile -Parent

# 检查目标文件是否存在
if (-not (Test-Path $targetFile)) {
    Write-Host "`n❌ 错误：目标文件不存在！" -ForegroundColor Red
    Write-Host "   路径: $targetFile" -ForegroundColor Yellow
    exit 1
}

# 显示当前文件信息
$currentFile = Get-Item $targetFile
Write-Host "`n📋 当前库文件信息：" -ForegroundColor Cyan
Write-Host "  路径: $($currentFile.FullName)" -ForegroundColor White
Write-Host "  大小: $([math]::Round($currentFile.Length / 1MB, 2)) MB ($($currentFile.Length) 字节)" -ForegroundColor White
Write-Host "  版本: tiny_publish (缺少操作符)" -ForegroundColor Red

# 提示用户输入源文件路径
Write-Host "`n📥 请提供解压后的库文件路径：" -ForegroundColor Yellow
Write-Host "   示例: inference_lite_lib.android.armv8\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so" -ForegroundColor Gray
Write-Host "   或者: C:\Users\YourName\Downloads\inference_lite_lib.android.armv8\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so" -ForegroundColor Gray
$sourceFile = Read-Host "`n请输入源文件路径"

# 检查源文件是否存在
if (-not (Test-Path $sourceFile)) {
    Write-Host "`n❌ 错误：源文件不存在！" -ForegroundColor Red
    Write-Host "   路径: $sourceFile" -ForegroundColor Yellow
    exit 1
}

# 显示源文件信息
$sourceFileInfo = Get-Item $sourceFile
Write-Host "`n📋 源文件信息：" -ForegroundColor Cyan
Write-Host "  路径: $($sourceFileInfo.FullName)" -ForegroundColor White
Write-Host "  大小: $([math]::Round($sourceFileInfo.Length / 1MB, 2)) MB ($($sourceFileInfo.Length) 字节)" -ForegroundColor White

# 验证文件大小（with_extra 版本应该更大）
if ($sourceFileInfo.Length -lt $currentFile.Length) {
    Write-Host "`n⚠️  警告：源文件比当前文件小！" -ForegroundColor Yellow
    Write-Host "   这可能不是 with_extra 版本，请确认。" -ForegroundColor Yellow
    $confirm = Read-Host "   是否继续？(y/n)"
    if ($confirm -ne "y" -and $confirm -ne "Y") {
        Write-Host "`n❌ 操作已取消" -ForegroundColor Red
        exit 0
    }
} else {
    Write-Host "   ✅ 文件大小正常（with_extra 版本）" -ForegroundColor Green
}

# 确认替换
Write-Host "`n⚠️  确认替换：" -ForegroundColor Yellow
Write-Host "   将替换: $targetFile" -ForegroundColor White
Write-Host "   使用: $sourceFile" -ForegroundColor White
$confirm = Read-Host "`n是否继续？(y/n)"

if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "`n❌ 操作已取消" -ForegroundColor Red
    exit 0
}

# 备份旧文件
$backupFile = "$targetFile.backup"
Write-Host "`n💾 备份旧文件..." -ForegroundColor Cyan
try {
    Copy-Item $targetFile $backupFile -Force
    Write-Host "   ✅ 备份完成: $backupFile" -ForegroundColor Green
} catch {
    Write-Host "   ❌ 备份失败: $_" -ForegroundColor Red
    exit 1
}

# 替换文件
Write-Host "`n🔄 替换文件..." -ForegroundColor Cyan
try {
    # 确保目标目录存在
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    
    Copy-Item $sourceFile $targetFile -Force
    Write-Host "   ✅ 替换完成！" -ForegroundColor Green
} catch {
    Write-Host "   ❌ 替换失败: $_" -ForegroundColor Red
    Write-Host "   🔄 正在恢复备份..." -ForegroundColor Yellow
    Copy-Item $backupFile $targetFile -Force
    exit 1
}

# 验证新文件
$newFile = Get-Item $targetFile
Write-Host "`n✅ 替换成功！" -ForegroundColor Green
Write-Host "`n📋 新文件信息：" -ForegroundColor Cyan
Write-Host "  路径: $($newFile.FullName)" -ForegroundColor White
Write-Host "  大小: $([math]::Round($newFile.Length / 1MB, 2)) MB ($($newFile.Length) 字节)" -ForegroundColor White
Write-Host "  版本: with_extra (包含所有操作符)" -ForegroundColor Green

Write-Host "`n📌 下一步：" -ForegroundColor Cyan
Write-Host "   1. 在 Android Studio 中重新编译项目" -ForegroundColor White
Write-Host "   2. 运行应用测试" -ForegroundColor White
Write-Host "`n💡 如果需要恢复旧文件，使用备份：" -ForegroundColor Yellow
Write-Host "   Copy-Item `"$backupFile`" `"$targetFile`" -Force" -ForegroundColor Gray

