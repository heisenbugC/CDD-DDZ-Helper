import os
from roboflow import Roboflow
from ultralytics import YOLO

def main():
    # 1. Skip downloading dataset, use the one already downloaded locally
    print("Using local dataset...")
    
    # The dataset was saved in the 'poker--2' directory
    yaml_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "poker--2", "data.yaml"))
    
    # 2. Setup the YOLOv8 Nano model
    print("\nInitializing YOLOv8 Nano model...")
    model = YOLO('yolov8n.pt')
    
    # 3. Train the model
    # nvidia-smi detected your RTX 4070 SUPER as GPU index 0.
    print("\nStarting training on NVIDIA GeForce RTX 4070 SUPER (GPU 0)...")
    
    results = model.train(
        data=yaml_path,
        epochs=50,
        imgsz=640,
        device=0,     # GPU index 0
        batch=16,     # RTX 4070 Super with 12GB VRAM can easily handle batch 16
        project='runs',
        name='card_detector'
    )
    
    # 4. Export directly to LiteRT / TFLite
    print("\nExporting model to TFLite format...")
    # half=True ensures it generates a float16 model which is highly optimized for Android
    model.export(format='tflite', half=True)
    
    print("\nDone! Your new .tflite model will be inside the 'runs/card_detector/weights/' directory.")

if __name__ == "__main__":
    main()
