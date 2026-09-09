# 编译 APK 脚本（PowerShell）
# 用法：右键"使用 PowerShell 运行"，或在本目录执行 .\build-apk.ps1

Write-Host "=== 编译 Android APK ===" -ForegroundColor Green

# 进入脚本所在目录（即项目根目录）
Set-Location $PSScriptRoot

# Android SDK 路径：默认 %LOCALAPPDATA%\Android\Sdk，装在别处请改这里
$sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
if (!(Test-Path $sdkDir)) { $sdkDir = "$env:USERPROFILE\Android\Sdk" }
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir

# JDK 17 路径：按需修改，或已配置 JAVA_HOME 时自动跳过
if (!$env:JAVA_HOME) {
    $jdkDir = "C:\Program Files\Java\jdk-17"
    if (Test-Path $jdkDir) { $env:JAVA_HOME = $jdkDir } else {
        Write-Host "错误: 未找到 JDK 17，请安装或设置 JAVA_HOME" -ForegroundColor Red
        exit 1
    }
}
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

if (!(Test-Path $sdkDir)) {
    Write-Host "错误: 未找到 Android SDK，请先安装并在 Android Studio 中打开一次项目" -ForegroundColor Red
    exit 1
}

Write-Host "开始编译（首次会下载依赖，需等待）..."
& .\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=== 编译成功! ===" -ForegroundColor Green
    Write-Host "APK 位于: $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
    Write-Host ""
    Write-Host "手机连接 USB 后可用 adb 安装："
    Write-Host "  adb install -r app\build\outputs\apk\debug\app-debug.apk"
} else {
    Write-Host "编译失败，请检查上方错误信息" -ForegroundColor Red
}
