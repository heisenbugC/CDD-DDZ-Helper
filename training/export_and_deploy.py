import os
import shutil
import subprocess

def find_adb():
    # 1. PATH lookup
    adb = shutil.which("adb")
    if adb:
        return adb
    
    # 2. ANDROID_HOME / ANDROID_SDK_ROOT environment lookup
    for env_var in ["ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_SDK"]:
        base = os.environ.get(env_var)
        if base:
            candidate = os.path.join(base, "platform-tools", "adb.exe")
            if os.path.exists(candidate):
                return candidate
                
    # 3. Known standard Windows Android SDK paths
    candidates = [
        r"D:\Users\koonlam\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        r"C:\Users\koonlam\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    return None

def get_connected_devices(adb_exe):
    if not adb_exe or not os.path.exists(adb_exe):
        return []
    try:
        res = subprocess.run([adb_exe, "devices"], capture_output=True, text=True, timeout=5)
        devices = []
        for line in res.stdout.splitlines():
            line = line.strip()
            if not line or line.startswith("List of devices"):
                continue
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                devices.append(parts[0])
        return devices
    except Exception as e:
        print(f"Warning checking adb devices: {e}")
        return []

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    candidates = [
        os.path.join(root_dir, "runs", "card_detector", "weights", "best.onnx"),
        os.path.join(root_dir, "runs", "detect", "runs", "card_detector", "weights", "best.onnx"),
    ]
    onnx_path = next((p for p in candidates if os.path.exists(p)), candidates[0])
    output_tflite_dir = os.path.join(root_dir, "training", "tflite_model_ddz_s")
    android_asset = os.path.join(root_dir, "composeApp", "src", "androidMain", "assets", "card_model.tflite")
    ios_asset = os.path.join(root_dir, "iosApp", "iosApp", "card_model.tflite")
    onnx2tf_exe = r"K:\data_science_venv\Scripts\onnx2tf.exe"
    
    print("=" * 60)
    print("Step 3: Convert ONNX -> TFLite and Deploy to Android & iOS")
    print("=" * 60)
    
    if not os.path.exists(onnx_path):
        print(f"ERROR: {onnx_path} does not exist.")
        print("Please ensure Step 2 (training) has finished.")
        return
        
    # 1. Run onnx2tf
    print("\nRunning onnx2tf conversion...")
    cmd = [onnx2tf_exe, "-i", onnx_path, "-o", output_tflite_dir]
    ret = subprocess.run(cmd)
    if ret.returncode != 0:
        print("ERROR: onnx2tf conversion failed.")
        return
        
    # 2. Copy generated float32 TFLite model to androidMain/assets and iosApp/iosApp
    src_tflite = os.path.join(output_tflite_dir, "best_float32.tflite")
    if not os.path.exists(src_tflite):
        print(f"ERROR: {src_tflite} not found.")
        return
        
    print(f"\nCopying {src_tflite} -> {android_asset}...")
    os.makedirs(os.path.dirname(android_asset), exist_ok=True)
    shutil.copy(src_tflite, android_asset)
    size_mb = os.path.getsize(android_asset) / (1024 * 1024)
    print(f"SUCCESS: Deployed card_model.tflite ({size_mb:.2f} MB) to Android assets.")

    print(f"\nCopying {src_tflite} -> {ios_asset}...")
    os.makedirs(os.path.dirname(ios_asset), exist_ok=True)
    shutil.copy(src_tflite, ios_asset)
    ios_size_mb = os.path.getsize(ios_asset) / (1024 * 1024)
    print(f"SUCCESS: Deployed card_model.tflite ({ios_size_mb:.2f} MB) to iOS bundle.")
    
    # 3. Assemble Android Debug APK
    print("\nRebuilding Android APK...")
    gradlew = os.path.join(root_dir, "gradlew.bat")
    build_cmd = [gradlew, ":androidApp:assembleDebug"]
    subprocess.run(build_cmd, cwd=root_dir)
    
    apk_path = os.path.join(root_dir, "androidApp", "build", "outputs", "apk", "debug", "androidApp-debug.apk")
    if os.path.exists(apk_path):
        print("\n" + "=" * 60)
        print("SUCCESS! Debug APK built with new model:")
        print(apk_path)
        print("=" * 60)
        
        # 4. Check for connected physical Android device and auto-install
        adb_exe = find_adb()
        if adb_exe:
            devices = get_connected_devices(adb_exe)
            if devices:
                print("\n" + "=" * 60)
                print(f"Physical Android device detected: {devices}")
                print("Installing updated APK directly via ADB...")
                print("=" * 60)
                for dev_id in devices:
                    print(f"Installing {os.path.basename(apk_path)} to device [{dev_id}]...")
                    install_res = subprocess.run([adb_exe, "-s", dev_id, "install", "-r", apk_path])
                    if install_res.returncode == 0:
                        print(f"[SUCCESS] APK successfully installed to device [{dev_id}]!")
                        # Launch the app
                        launch_res = subprocess.run([
                            adb_exe, "-s", dev_id, "shell", "am", "start", "-n",
                            "com.example.cddddzhelper/.MainActivity"
                        ])
                        if launch_res.returncode == 0:
                            print(f"[SUCCESS] App launched on device [{dev_id}]!")
                    else:
                        print(f"[WARNING] ADB install to device [{dev_id}] failed (code: {install_res.returncode})")
            else:
                print("\n[INFO] No physical Android device attached via ADB. Skipping auto-install.")
        else:
            print("\n[INFO] ADB executable not found. Skipping auto-install.")

if __name__ == "__main__":
    main()
