import os
import glob
import shutil
import yaml
from roboflow import Roboflow
from ultralytics import YOLO

def main():
    # ==========================================
    # --- CONFIGURATION ---
    # Put the Roboflow details of the Jokers dataset you found here!
    ROBOFLOW_API_KEY = "REDACTED_API_KEY" # Your API key
    JOKER_WORKSPACE = "rummy-qmgrc"
    JOKER_PROJECT = "jokers-lr1mz"
    JOKER_VERSION = 1
    # ==========================================
    
    # 1. Download Jokers Dataset
    print("Downloading Jokers Dataset from Roboflow...")
    rf = Roboflow(api_key=ROBOFLOW_API_KEY)
    joker_proj = rf.workspace(JOKER_WORKSPACE).project(JOKER_PROJECT)
    joker_dataset = joker_proj.version(JOKER_VERSION).download("yolov8")
    
    jokers_dir = joker_dataset.location
    poker_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "poker--2"))
    
    # 2. Merge Datasets (Offset Joker IDs by +52)
    print("\nMerging Jokers into Poker dataset...")
    class_id_offset = 52 
    for split in ["train", "valid", "test"]:
        joker_lbl_dir = os.path.join(jokers_dir, split, "labels")
        joker_img_dir = os.path.join(jokers_dir, split, "images")
        poker_lbl_dir = os.path.join(poker_dir, split, "labels")
        poker_img_dir = os.path.join(poker_dir, split, "images")
        
        if not os.path.exists(joker_lbl_dir): continue
        if not os.path.exists(poker_lbl_dir): os.makedirs(poker_lbl_dir)
        if not os.path.exists(poker_img_dir): os.makedirs(poker_img_dir)
        
        for lbl_file in glob.glob(os.path.join(joker_lbl_dir, "*.txt")):
            with open(lbl_file, "r") as f:
                lines = f.readlines()
                
            new_lines = []
            for line in lines:
                parts = line.strip().split()
                if not parts: continue
                # Shift class ID so 0 -> 52, 1 -> 53
                original_class = int(parts[0])
                new_class = original_class + class_id_offset
                parts[0] = str(new_class)
                new_lines.append(" ".join(parts))
            
            # Save into the poker directory with a 'joker_' prefix to avoid overwriting
            base_name = os.path.basename(lbl_file)
            dest_lbl = os.path.join(poker_lbl_dir, f"joker_{base_name}")
            with open(dest_lbl, "w") as f:
                f.write("\n".join(new_lines) + "\n")
                
            # Copy corresponding image
            img_name = base_name.replace(".txt", ".jpg")
            src_img = os.path.join(joker_img_dir, img_name)
            dest_img = os.path.join(poker_img_dir, f"joker_{img_name}")
            if os.path.exists(src_img):
                shutil.copy(src_img, dest_img)

    # 3. Update data.yaml automatically
    print("\nUpdating data.yaml to include 54 classes...")
    yaml_path = os.path.join(poker_dir, "data.yaml")
    with open(yaml_path, 'r') as f:
        data = yaml.safe_load(f)
    
    data['nc'] = 54
    if 'Black_Joker' not in data['names']:
        data['names'].append('Black_Joker')
    if 'Red_Joker' not in data['names']:
        data['names'].append('Red_Joker')
        
    with open(yaml_path, 'w') as f:
        yaml.safe_dump(data, f)
        
    # 4. Train the model on the combined dataset
    print("\nStarting YOLOv8 Training on combined 54-class dataset (using YOLOv8s)...")
    model = YOLO('yolov8s.pt')
    results = model.train(
        data=yaml_path,
        epochs=50,
        imgsz=640,
        device=0,     # Using your RTX 4070 SUPER
        batch=16,
        workers=0,    # Fix Windows PyTorch DataLoader crash
        project='runs',
        name='card_detector'
    )
    
    # 5. Export directly to LiteRT (TFLite)
    print("\nExporting model to LiteRT format...")
    # NOTE: Since you are on Windows, we export to ONNX first as a workaround for the Ultralytics bug,
    # then you can use onnx2tf, OR just use model.export(format='onnx') and do it in Colab.
    # To keep this fully automated and local, we'll export to ONNX.
    model.export(format='onnx')
    print(r"\nDone! Your DDZ model is in 'runs\card_detector\weights\best.onnx'.")
    print(r"You can convert it to TFLite by running:")
    print(r'K:\data_science_venv\Scripts\onnx2tf.exe -i "runs\card_detector\weights\best.onnx" -o "training\tflite_model_ddz_s"')

if __name__ == "__main__":
    main()
