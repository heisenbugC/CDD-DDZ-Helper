import os
import shutil
import subprocess

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
        
        # Check if device is attached for auto-install
        adb_check = subprocess.run(["adb", "devices"], capture_output=True, text=True)
        lines = [l for l in adb_check.stdout.splitlines() if l.strip() and not l.startswith("List")]
        if lines:
            print("\nDevice detected! Installing to device...")
            subprocess.run([gradlew, ":androidApp:installDebug"], cwd=root_dir)
            print("Installed successfully to device!")

if __name__ == "__main__":
    main()
