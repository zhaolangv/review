# PaddleOCR 文件下载脚本
# 下载集成 PaddleOCR 所需的所有文件

$ErrorActionPreference = "Stop"

Write-Host "开始下载 PaddleOCR 文件..." -ForegroundColor Green

# 创建临时目录
$tempDir = "temp_paddleocr"
if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir
}
New-Item -ItemType Directory -Path $tempDir | Out-Null

# 创建目标目录
$libsDir = "app\src\main\jniLibs"
$assetsDir = "app\src\main\assets\paddleocr"
if (-not (Test-Path $libsDir)) {
    New-Item -ItemType Directory -Path $libsDir -Force | Out-Null
}
if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
}

# 下载文件列表
$files = @(
    @{
        Url = "https://paddleocr.bj.bcebos.com/libs/paddle_lite_libs_v2_10.tar.gz"
        Dest = "$tempDir\paddle_lite.tar.gz"
        ExtractTo = "$tempDir\paddle_lite"
    },
    @{
        Url = "https://paddleocr.bj.bcebos.com/PP-OCRv2/lite/ch_PP-OCRv2.tar.gz"
        Dest = "$tempDir\models.tar.gz"
        ExtractTo = "$assetsDir\models"
    },
    @{
        Url = "https://paddleocr.bj.bcebos.com/dygraph_v2.0/lite/ch_dict.tar.gz"
        Dest = "$tempDir\dict.tar.gz"
        ExtractTo = "$assetsDir\labels"
    }
)

# 下载函数
function Download-File {
    param($Url, $Dest)
    Write-Host "下载: $Url" -ForegroundColor Yellow
    Write-Host "保存到: $Dest" -ForegroundColor Yellow
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
        Write-Host "下载完成: $Dest" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "下载失败: $_" -ForegroundColor Red
        return $false
    }
}

# 解压 tar.gz 文件（需要 7-Zip 或 tar 命令）
function Extract-TarGz {
    param($Archive, $Dest)
    Write-Host "解压: $Archive" -ForegroundColor Yellow
    Write-Host "解压到: $Dest" -ForegroundColor Yellow
    
    if (-not (Test-Path $Dest)) {
        New-Item -ItemType Directory -Path $Dest -Force | Out-Null
    }
    
    # 尝试使用 tar 命令（Windows 10+ 内置）
    $tarCommand = Get-Command tar -ErrorAction SilentlyContinue
    if ($tarCommand) {
        & tar -xzf $Archive -C $Dest
        Write-Host "解压完成" -ForegroundColor Green
        return $true
    }
    
    Write-Host "警告: 未找到 tar 命令，请手动解压 $Archive 到 $Dest" -ForegroundColor Yellow
    return $false
}

# 下载所有文件
foreach ($file in $files) {
    $success = Download-File -Url $file.Url -Dest $file.Dest
    if ($success) {
        Extract-TarGz -Archive $file.Dest -Dest $file.ExtractTo
    }
}

# 复制 .so 文件
Write-Host "`n查找 .so 文件..." -ForegroundColor Green
$soFiles = Get-ChildItem -Path "$tempDir\paddle_lite" -Recurse -Filter "*.so" -ErrorAction SilentlyContinue
if ($soFiles) {
    foreach ($soFile in $soFiles) {
        # 根据架构创建目录
        $arch = if ($soFile.DirectoryName -like "*arm64*") { "arm64-v8a" } 
                elseif ($soFile.DirectoryName -like "*armv7*" -or $soFile.DirectoryName -like "*armeabi*") { "armeabi-v7a" }
                else { "unknown" }
        
        if ($arch -ne "unknown") {
            $targetDir = "$libsDir\$arch"
            if (-not (Test-Path $targetDir)) {
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }
            Copy-Item -Path $soFile.FullName -Destination "$targetDir\$($soFile.Name)" -Force
            Write-Host "复制: $($soFile.Name) -> $targetDir" -ForegroundColor Green
        }
    }
}

Write-Host "`n下载完成！" -ForegroundColor Green
Write-Host "请检查以下目录:" -ForegroundColor Yellow
Write-Host "  - $libsDir (库文件)" -ForegroundColor Yellow
Write-Host "  - $assetsDir (模型文件)" -ForegroundColor Yellow
Write-Host "`n如果解压失败，请手动解压以下文件:" -ForegroundColor Yellow
Write-Host "  - $tempDir\paddle_lite.tar.gz" -ForegroundColor Yellow
Write-Host "  - $tempDir\models.tar.gz" -ForegroundColor Yellow
Write-Host "  - $tempDir\dict.tar.gz" -ForegroundColor Yellow

