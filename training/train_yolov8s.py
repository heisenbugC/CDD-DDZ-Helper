import os
import shutil
from ultralytics import YOLO

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    yaml_path = os.path.join(root_dir, "training", "poker--2", "data.yaml")
    
    print("=" * 60)
    print("Starting YOLOv8s Training for DDZ 54-Card Detection")
    print("Dataset config:", yaml_path)
    print("=" * 60)
    
    # Initialize YOLOv8 Small model
    model = YOLO("yolov8s.pt")
    
    # Train model with color-safe hyperparameters for Joker disambiguation
    results = model.train(
        data=yaml_path,
        epochs=50,
        imgsz=640,
        device=0,         # NVIDIA GeForce RTX 4070 SUPER
        batch=16,         # Batch size 16 for 12GB VRAM
        workers=0,        # Required on Windows to prevent PyTorch DataLoader crash
        project="runs",
        name="card_detector",
        exist_ok=True,    # Overwrite/save directly into runs/card_detector
        hsv_h=0.005,      # Restrict hue jitter to preserve Red Joker hue
        hsv_s=0.25,       # Moderate saturation jitter to prevent Red Joker desaturating into Black Joker
        hsv_v=0.30        # Moderate value jitter
    )
    
    print("\nTraining completed! Exporting best model to ONNX...")
    save_dir = getattr(results, "save_dir", None)
    candidates = []
    if save_dir:
        candidates.append(os.path.join(save_dir, "weights", "best.pt"))
    candidates.extend([
        os.path.join(root_dir, "runs", "detect", "runs", "card_detector", "weights", "best.pt"),
        os.path.join(root_dir, "runs", "card_detector", "weights", "best.pt")
    ])
    
    best_weights = next((p for p in candidates if os.path.exists(p)), None)
    if not best_weights:
        raise FileNotFoundError(f"Could not find best.pt in any candidate location: {candidates}")
        
    print("Found best weights at:", best_weights)
    best_model = YOLO(best_weights)
    best_model.export(format="onnx")
    
    onnx_path = best_weights.replace(".pt", ".onnx")
    # Mirror to root runs/card_detector/weights if needed
    dest_dir = os.path.join(root_dir, "runs", "card_detector", "weights")
    os.makedirs(dest_dir, exist_ok=True)
    if os.path.exists(best_weights):
        shutil.copy(best_weights, os.path.join(dest_dir, "best.pt"))
    if os.path.exists(onnx_path):
        shutil.copy(onnx_path, os.path.join(dest_dir, "best.onnx"))

    print("\n" + "=" * 60)
    print("SUCCESS: Model trained and exported to ONNX!")
    print("ONNX Model location:", onnx_path)
    print("=" * 60)

if __name__ == "__main__":
    main()
