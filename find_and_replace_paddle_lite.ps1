# 智能搜索并替换 Paddle Lite 库文件
# 自动搜索常见位置，找到后自动替换

$ErrorActionPreference = "Stop"

Write-Host "`n🔍 智能搜索 Paddle Lite with_extra 库文件..." -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Gray

# 目标文件路径
$targetFile = "paddle_lite_libs_v2_10\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so"
$targetDir = Split-Path $targetFile -Parent

# 搜索路径列表
$searchPaths = @(
    # 当前目录
    ".\inference_lite_lib.android.armv8\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    ".\inference_lite_lib\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    
    # 用户目录
    "$env:USERPROFILE\Downloads\inference_lite_lib.android.armv8\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    "$env:USERPROFILE\Downloads\inference_lite_lib\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    "$env:USERPROFILE\Desktop\inference_lite_lib.android.armv8\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    "$env:USERPROFILE\Desktop\inference_lite_lib\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    "$env:USERPROFILE\Documents\inference_lite_lib.android.armv8\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    "$env:USERPROFILE\Documents\inference_lite_lib\cxx\libs\arm64-v8a\libpaddle_light_api_shared.so",
    
    # 递归搜索（在 Downloads 和 Desktop 中）
    "$env:USERPROFILE\Downloads\**\libpaddle_light_api_shared.so",
    "$env:USERPROFILE\Desktop\**\libpaddle_light_api_shared.so"
)

$foundFiles = @()

# 搜索文件
Write-Host "`n📂 正在搜索..." -ForegroundColor Yellow
foreach ($path in $searchPaths) {
    try {
        if ($path -like "**\*") {
            # 递归搜索
            $files = Get-ChildItem -Path (Split-Path $path -Parent) -Filter "libpaddle_light_api_shared.so" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -like "*arm64-v8a*" }
            foreach ($file in $files) {
                if ($file.Length -gt 8MB) {  # with_extra 版本应该 > 8MB
                    $foundFiles += $file
                }
            }
        } else {
            if (Test-Path $path) {
                $file = Get-Item $path
                if ($file.Length -gt 8MB) {  # with_extra 版本应该 > 8MB
                    $foundFiles += $file
                }
            }
        }
    } catch {
        # 忽略错误，继续搜索
    }
}

# 去重
$foundFiles = $foundFiles | Sort-Object FullName -Unique

if ($foundFiles.Count -eq 0) {
    Write-Host "`nNot found: with_extra version library file" -ForegroundColor Red
    Write-Host "`nPlease manually specify file path:" -ForegroundColor Yellow
    Write-Host "  1. Extract downloaded file" -ForegroundColor White
    Write-Host "  2. Find: inference_lite_lib.android.armv8/cxx/libs/arm64-v8a/libpaddle_light_api_shared.so" -ForegroundColor White
    Write-Host "  3. Run: .\replace_paddle_lite_lib.ps1" -ForegroundColor White
    Write-Host "  4. Enter file path" -ForegroundColor White
    exit 1
}

# 显示找到的文件
Write-Host "`nFound $($foundFiles.Count) candidate file(s):" -ForegroundColor Green
for ($i = 0; $i -lt $foundFiles.Count; $i++) {
    $file = $foundFiles[$i]
    Write-Host "`n[$($i + 1)] $($file.FullName)" -ForegroundColor Cyan
    Write-Host "    Size: $([math]::Round($file.Length / 1MB, 2)) MB" -ForegroundColor White
}

# 选择文件
$selectedFile = $null
if ($foundFiles.Count -eq 1) {
    $selectedFile = $foundFiles[0]
    Write-Host "`nAuto-selected the only file" -ForegroundColor Green
} else {
    Write-Host "`nPlease select file to use (1-$($foundFiles.Count)): " -ForegroundColor Yellow -NoNewline
    $choice = Read-Host
    $index = [int]$choice - 1
    if ($index -ge 0 -and $index -lt $foundFiles.Count) {
        $selectedFile = $foundFiles[$index]
    } else {
        Write-Host "`nInvalid selection" -ForegroundColor Red
        exit 1
    }
}

# 显示当前文件信息
if (Test-Path $targetFile) {
    $currentFile = Get-Item $targetFile
    Write-Host "`nCurrent library file:" -ForegroundColor Cyan
    Write-Host "  Path: $($currentFile.FullName)" -ForegroundColor White
    Write-Host "  Size: $([math]::Round($currentFile.Length / 1MB, 2)) MB" -ForegroundColor White
    Write-Host "  Version: tiny_publish (missing operators)" -ForegroundColor Red
}

# 显示新文件信息
Write-Host "`nNew library file:" -ForegroundColor Cyan
Write-Host "  Path: $($selectedFile.FullName)" -ForegroundColor White
Write-Host "  Size: $([math]::Round($selectedFile.Length / 1MB, 2)) MB" -ForegroundColor White
Write-Host "  Version: with_extra (includes all operators)" -ForegroundColor Green

# 确认替换
Write-Host "`nConfirm replacement? (y/n): " -ForegroundColor Yellow -NoNewline
$confirm = Read-Host

if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "`nOperation cancelled" -ForegroundColor Red
    exit 0
}

# 备份旧文件
if (Test-Path $targetFile) {
    $backupFile = "$targetFile.backup"
    Write-Host "`nBacking up old file..." -ForegroundColor Cyan
    try {
        Copy-Item $targetFile $backupFile -Force
        Write-Host "  Backup completed: $backupFile" -ForegroundColor Green
    } catch {
        Write-Host "  Backup failed: $_" -ForegroundColor Red
        exit 1
    }
}

# 替换文件
Write-Host "`nReplacing file..." -ForegroundColor Cyan
try {
    # 确保目标目录存在
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    
    Copy-Item $selectedFile.FullName $targetFile -Force
    Write-Host "  Replacement completed!" -ForegroundColor Green
} catch {
    Write-Host "  Replacement failed: $_" -ForegroundColor Red
    if (Test-Path $backupFile) {
        Write-Host "  Restoring backup..." -ForegroundColor Yellow
        Copy-Item $backupFile $targetFile -Force
    }
    exit 1
}

# 验证新文件
$newFile = Get-Item $targetFile
Write-Host "`nSuccess! File replaced." -ForegroundColor Green
Write-Host "`nNew file info:" -ForegroundColor Cyan
Write-Host "  Path: $($newFile.FullName)" -ForegroundColor White
Write-Host "  Size: $([math]::Round($newFile.Length / 1MB, 2)) MB ($($newFile.Length) bytes)" -ForegroundColor White
Write-Host "  Version: with_extra (includes all operators)" -ForegroundColor Green

Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "  1. Rebuild project in Android Studio (Build -> Rebuild Project)" -ForegroundColor White
Write-Host "  2. Run and test the app" -ForegroundColor White
Write-Host "  3. Should no longer see hard_swish operator missing error" -ForegroundColor White

Write-Host "`nTo restore old file:" -ForegroundColor Yellow
Write-Host "  Copy-Item `"$backupFile`" `"$targetFile`" -Force" -ForegroundColor Gray

