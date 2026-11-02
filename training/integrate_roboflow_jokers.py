import os
import sys
import glob
import shutil
import argparse
import yaml

def find_roboflow_dataset_dir(base_dir):
    """
    Finds the directory containing data.yaml inside base_dir,
    handling cases where the user extracted into a subfolder.
    """
    if os.path.exists(os.path.join(base_dir, "data.yaml")):
        return base_dir
    
    # Check 1 level down
    for item in os.listdir(base_dir):
        sub = os.path.join(base_dir, item)
        if os.path.isdir(sub) and os.path.exists(os.path.join(sub, "data.yaml")):
            return sub
            
    # Check recursively up to depth 3
    for root, dirs, files in os.walk(base_dir):
        if "data.yaml" in files:
            return root
            
    return None

def strip_old_joker_data(poker_dir):
    """
    Completely removes all existing joker images and labels from poker--2.
    Also strips any class 52 or 53 lines from any remaining label files.
    """
    print("\n" + "=" * 60)
    print("STEP 1: Stripping out all old Joker classes from poker--2")
    print("=" * 60)
    
    deleted_lbls = 0
    deleted_imgs = 0
    cleaned_lbls = 0

    for split in ["train", "valid", "test"]:
        lbl_dir = os.path.join(poker_dir, split, "labels")
        img_dir = os.path.join(poker_dir, split, "images")
        
        if not os.path.exists(lbl_dir):
            continue
            
        # Delete any file with 'joker' in the name
        for lbl_path in glob.glob(os.path.join(lbl_dir, "*")):
            base = os.path.basename(lbl_path).lower()
            if "joker" in base:
                os.remove(lbl_path)
                deleted_lbls += 1
            else:
                # In remaining files, verify no lines contain class 52 or 53
                try:
                    with open(lbl_path, "r", encoding="utf-8") as f:
                        lines = f.readlines()
                    filtered = [l for l in lines if l.strip() and int(l.strip().split()[0]) < 52]
                    if len(filtered) != len(lines):
                        with open(lbl_path, "w", encoding="utf-8") as f:
                            f.writelines(filtered)
                        cleaned_lbls += 1
                except Exception:
                    pass

        if os.path.exists(img_dir):
            for img_path in glob.glob(os.path.join(img_dir, "*")):
                base = os.path.basename(img_path).lower()
                if "joker" in base:
                    os.remove(img_path)
                    deleted_imgs += 1

    print(f"  [OK] Deleted {deleted_lbls} old joker label files.")
    print(f"  [OK] Deleted {deleted_imgs} old joker image files.")
    if cleaned_lbls > 0:
        print(f"  [OK] Cleaned {cleaned_lbls} label files that had lingering joker annotations.")
    print("  [SUCCESS] Old joker classes completely purged from dataset.")

def parse_roboflow_classes(rf_yaml_path):
    """
    Reads Roboflow data.yaml and dynamically identifies which class index
    corresponds to Black Joker and which to Red Joker.
    """
    with open(rf_yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
        
    names = data.get("names", [])
    if isinstance(names, dict):
        # Convert {0: 'black_joker', 1: 'red_joker'} to sorted list
        names = [names[k] for k in sorted(names.keys())]
        
    print(f"\nRoboflow dataset classes detected: {names}")
    
    black_idx = None
    red_idx = None
    
    for idx, name in enumerate(names):
        norm = name.lower().replace("-", "_").replace(" ", "_")
        if any(k in norm for k in ["black", "hei", "xiao", "small", "mono"]):
            black_idx = idx
        elif any(k in norm for k in ["red", "hong", "da", "big", "color"]):
            red_idx = idx

    return names, black_idx, red_idx

def integrate_roboflow_jokers(rf_dir, poker_dir, black_override=None, red_override=None):
    """
    Integrates the Roboflow dataset into poker--2:
    - Remaps Black Joker -> Class 52
    - Remaps Red Joker   -> Class 53
    """
    rf_yaml = os.path.join(rf_dir, "data.yaml")
    names, black_idx, red_idx = parse_roboflow_classes(rf_yaml)
    
    if black_override is not None:
        black_idx = black_override
    if red_override is not None:
        red_idx = red_override
        
    if black_idx is None or red_idx is None:
        print("\nERROR: Could not automatically distinguish Black Joker vs Red Joker classes.")
        print(f"Found class names: {names}")
        print("Please specify manual indices via --black_id and --red_id arguments.")
        sys.exit(1)
        
    print(f"  -> Black Joker identified as Roboflow class {black_idx} ('{names[black_idx]}') => Target Class 52")
    print(f"  -> Red Joker identified as Roboflow class {red_idx} ('{names[red_idx]}') => Target Class 53")
    
    print("\n" + "=" * 60)
    print("STEP 2: Remapping and Appending New Roboflow Jokers to poker--2")
    print("=" * 60)
    
    stats = {
        "train": {"52": 0, "53": 0, "images": 0},
        "valid": {"52": 0, "53": 0, "images": 0},
        "test":  {"52": 0, "53": 0, "images": 0}
    }
    
    split_map = {
        "train": "train",
        "valid": "valid",
        "val": "valid",
        "test": "test"
    }

    for rf_split in ["train", "valid", "val", "test"]:
        src_lbl_dir = os.path.join(rf_dir, rf_split, "labels")
        src_img_dir = os.path.join(rf_dir, rf_split, "images")
        
        target_split = split_map.get(rf_split)
        if not target_split or not os.path.exists(src_lbl_dir):
            continue
            
        dest_lbl_dir = os.path.join(poker_dir, target_split, "labels")
        dest_img_dir = os.path.join(poker_dir, target_split, "images")
        os.makedirs(dest_lbl_dir, exist_ok=True)
        os.makedirs(dest_img_dir, exist_ok=True)
        
        for lbl_file in glob.glob(os.path.join(src_lbl_dir, "*.txt")):
            with open(lbl_file, "r", encoding="utf-8") as f:
                lines = f.readlines()
                
            new_lines = []
            for line in lines:
                parts = line.strip().split()
                if not parts:
                    continue
                orig_cls = int(parts[0])
                if orig_cls == black_idx:
                    parts[0] = "52"
                    new_lines.append(" ".join(parts))
                    stats[target_split]["52"] += 1
                elif orig_cls == red_idx:
                    parts[0] = "53"
                    new_lines.append(" ".join(parts))
                    stats[target_split]["53"] += 1
                else:
                    print(f"Warning: Unexpected class ID {orig_cls} in {lbl_file}")

            if new_lines:
                base_name = os.path.basename(lbl_file)
                dest_lbl_file = os.path.join(dest_lbl_dir, f"rf_joker_{base_name}")
                with open(dest_lbl_file, "w", encoding="utf-8") as f:
                    f.write("\n".join(new_lines) + "\n")
                    
                # Copy corresponding image
                base_stem = os.path.splitext(base_name)[0]
                img_found = False
                for ext in [".jpg", ".jpeg", ".png", ".bmp", ".webp"]:
                    candidate = os.path.join(src_img_dir, base_stem + ext)
                    if os.path.exists(candidate):
                        dest_img = os.path.join(dest_img_dir, f"rf_joker_{base_stem}{ext}")
                        shutil.copy(candidate, dest_img)
                        stats[target_split]["images"] += 1
                        img_found = True
                        break
                        
                if not img_found:
                    print(f"Warning: Matching image for {base_name} not found in {src_img_dir}")

    for s, data in stats.items():
        print(f"  [{s.upper()}] Added {data['images']} images (Class 52 Black Joker: {data['52']}, Class 53 Red Joker: {data['53']})")

    # Update poker--2/data.yaml
    print("\n" + "=" * 60)
    print("STEP 3: Verifying and Updating poker--2/data.yaml")
    print("=" * 60)
    
    poker_yaml = os.path.join(poker_dir, "data.yaml")
    with open(poker_yaml, "r", encoding="utf-8") as f:
        poker_data = yaml.safe_load(f)
        
    poker_data["nc"] = 54
    poker_names = poker_data.get("names", [])
    
    # Ensure length is at least 54
    while len(poker_names) < 52:
        poker_names.append(str(len(poker_names)))
    if len(poker_names) == 52:
        poker_names.append("Black_Joker")
        poker_names.append("Red_Joker")
    elif len(poker_names) >= 54:
        poker_names[52] = "Black_Joker"
        poker_names[53] = "Red_Joker"
    elif len(poker_names) == 53:
        poker_names[52] = "Black_Joker"
        poker_names.append("Red_Joker")
        
    poker_data["names"] = poker_names
    with open(poker_yaml, "w", encoding="utf-8") as f:
        yaml.safe_dump(poker_data, f, sort_keys=False)
        
    print(f"  [OK] Saved {poker_yaml} with nc=54, Class 52='Black_Joker', Class 53='Red_Joker'.")
    print("\n" + "=" * 60)
    print("SUCCESS: Roboflow Joker Dataset integrated cleanly!")
    print("=" * 60)

def main():
    parser = argparse.ArgumentParser(description="Strip old jokers and integrate new Roboflow jokers into poker--2.")
    parser.add_argument("--roboflow_dir", type=str, default=None, help="Path to unzipped Roboflow dataset")
    parser.add_argument("--black_id", type=int, default=None, help="Roboflow class ID for Black Joker")
    parser.add_argument("--red_id", type=int, default=None, help="Roboflow class ID for Red Joker")
    parser.add_argument("--strip_only", action="store_true", help="Only strip old jokers without integrating new dataset")
    args = parser.parse_args()
    
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    poker_dir = os.path.join(root_dir, "training", "poker--2")
    
    # 1. Strip old jokers
    strip_old_joker_data(poker_dir)
    
    if args.strip_only:
        print("\n[INFO] --strip_only specified. Skipping integration of new dataset.")
        return

    if args.roboflow_dir:
        rf_base = os.path.abspath(args.roboflow_dir)
    else:
        rf_base = os.path.join(root_dir, "training", "roboflow_jokers")
        
    if not os.path.exists(rf_base):
        print(f"ERROR: Roboflow folder not found at: {rf_base}")
        print("Please export and unzip your Roboflow dataset into:")
        print(f"  {rf_base}")
        sys.exit(1)
        
    rf_dir = find_roboflow_dataset_dir(rf_base)
    if not rf_dir:
        print(f"ERROR: Could not find data.yaml in {rf_base} or any of its subdirectories.")
        sys.exit(1)
        
    print(f"Found Roboflow dataset at: {rf_dir}")
    print(f"Target Poker dataset at:   {poker_dir}")
    
    # 2. Integrate new roboflow jokers
    integrate_roboflow_jokers(rf_dir, poker_dir, args.black_id, args.red_id)

if __name__ == "__main__":
    main()
