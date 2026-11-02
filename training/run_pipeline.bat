@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   CDD/DDZ Card Detection: Joker Integration & Training
echo ============================================================
echo.

set PYTHON_EXE=K:\data_science_venv\Scripts\python.exe

if not exist "%PYTHON_EXE%" (
    echo [ERROR] Python not found at %PYTHON_EXE%
    echo Please make sure K:\data_science_venv is available.
    pause
    exit /b 1
)

echo [Step 1/3] Auto-Augmenting Jokers (1100+ balanced samples) and Purging Old Data...
"%PYTHON_EXE%" "%~dp0augment_jokers.py"
if %errorlevel% neq 0 (
    echo [ERROR] Integration failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [Step 2/3] Training YOLOv8s Model (50 epochs, GPU: 0)...
"%PYTHON_EXE%" "%~dp0train_yolov8s.py"
if %errorlevel% neq 0 (
    echo [ERROR] Training failed!
    pause
    exit /b %errorlevel%
)

echo.
echo [Step 3/3] Converting to TFLite and Deploying to Android/iOS Assets...
"%PYTHON_EXE%" "%~dp0export_and_deploy.py"
if %errorlevel% neq 0 (
    echo [ERROR] Deployment failed!
    pause
    exit /b %errorlevel%
)

echo.
echo ============================================================
echo   ALL STEPS COMPLETED SUCCESSFULLY!
echo ============================================================
pause

