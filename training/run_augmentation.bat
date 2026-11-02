@echo off
setlocal
cd /d "%~dp0.."

echo ================================================================
echo Starting Automated Joker Augmentation ^& Dataset Balancing
echo ================================================================

set PYTHON_EXE=K:\data_science_venv\Scripts\python.exe

if not exist "%PYTHON_EXE%" (
    echo ERROR: Python executable not found at %PYTHON_EXE%
    pause
    exit /b 1
)

"%PYTHON_EXE%" training\augment_jokers.py %*

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================================================
    echo Augmentation completed successfully!
    echo Check training\augment_preview.jpg to visually verify bounding boxes.
    echo Next step: run training\train_yolov8s.py to train the model.
    echo ================================================================
) else (
    echo.
    echo [ERROR] Augmentation failed with exit code %ERRORLEVEL%.
)

pause

