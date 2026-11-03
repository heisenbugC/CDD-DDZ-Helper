@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   CDD/DDZ Card Detection: Joker Hand Augmentation ^& Pipeline
echo ============================================================
echo.

set PYTHON_EXE=K:\data_science_venv\Scripts\python.exe

if not exist "%PYTHON_EXE%" (
    echo [ERROR] Python not found at %PYTHON_EXE%
    echo Please make sure K:\data_science_venv is available.
    pause
    exit /b 1
)

echo [Step 1/4] In-Hand ^& Fanned Joker Data Augmentation (1100+ balanced samples)...
"%PYTHON_EXE%" "%~dp0augment_jokers.py"
if %errorlevel% neq 0 (
    echo [ERROR] Augmentation failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [Step 2/4] Training YOLOv8s Model (50 epochs, GPU: 0)...
"%PYTHON_EXE%" "%~dp0train_yolov8s.py"
if %errorlevel% neq 0 (
    echo [ERROR] Training failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [Step 3/4] Converting to TFLite and Deploying to Android/iOS Assets...
"%PYTHON_EXE%" "%~dp0export_and_deploy.py"
if %errorlevel% neq 0 (
    echo [ERROR] Deployment failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [Step 4/4] Checking Physical Android Device for Auto-Install...
set ADB_EXE=D:\Users\koonlam\AppData\Local\Android\Sdk\platform-tools\adb.exe
if not exist "!ADB_EXE!" (
    for /f "tokens=*" %%i in ('where adb 2^>nul') do set ADB_EXE=%%i
)
if exist "!ADB_EXE!" (
    for /f "tokens=1,2" %%a in ('"!ADB_EXE!" devices ^| findstr /v "List"') do (
        if "%%b"=="device" (
            echo Physical Android device detected: %%a
            echo Installing updated APK to device %%a...
            "!ADB_EXE!" -s %%a install -r "%~dp0..\androidApp\build\outputs\apk\debug\androidApp-debug.apk"
            if !errorlevel! equ 0 (
                echo [SUCCESS] APK successfully installed to %%a!
                "!ADB_EXE!" -s %%a shell am start -n com.example.cddddzhelper/.MainActivity
                echo [SUCCESS] App launched on device!
            )
        )
    )
) else (
    echo [INFO] ADB not found or no device attached. Skipping APK install.
)

echo.
echo ============================================================
echo   ALL STEPS COMPLETED SUCCESSFULLY!
echo ============================================================
pause
