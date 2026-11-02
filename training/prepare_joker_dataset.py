import os
import glob
import shutil
import random
import cv2
import numpy as np
import yaml

def remove_old_joker_data(poker_dir):
    print("--- 1. Removing old/mislabeled joker files from poker--2 ---")
    deleted_lbls = 0
    deleted_imgs = 0
    for split in ["train", "valid", "test"]:
        lbl_dir = os.path.join(poker_dir, split, "labels")
        img_dir = os.path.join(poker_dir, split, "images")
        for f in glob.glob(os.path.join(lbl_dir, "*joker_*.txt")):
            os.remove(f)
            deleted_lbls += 1
        for f in glob.glob(os.path.join(img_dir, "*joker_*.jpg")):
            os.remove(f)
            deleted_imgs += 1
        for f in glob.glob(os.path.join(img_dir, "*joker_*.png")):
            os.remove(f)
            deleted_imgs += 1
    print(f"Deleted {deleted_lbls} old joker label files and {deleted_imgs} image files.")

def process_batman_cards(batman_dir, poker_dir):
    print("\n--- 2. Merging batman_cards jokers (Classes 5 -> 52, 36 -> 53) ---")
    if not os.path.exists(batman_dir):
        print(f"Directory {batman_dir} not found. Skipping.")
        return 0, 0

    count_52 = 0
    count_53 = 0

    for split in ["train", "valid", "test"]:
        lbl_dir = os.path.join(batman_dir, split, "labels")
        img_dir = os.path.join(batman_dir, split, "images")
        dest_lbl_dir = os.path.join(poker_dir, split, "labels")
        dest_img_dir = os.path.join(poker_dir, split, "images")

        if not os.path.exists(lbl_dir):
            continue

        for lbl_file in glob.glob(os.path.join(lbl_dir, "*.txt")):
            with open(lbl_file, "r") as f:
                lines = f.readlines()

            new_lines = []
            for line in lines:
                parts = line.strip().split()
                if not parts:
                    continue
                cls_id = int(parts[0])
                if cls_id == 5:  # black joker in batman_cards
                    parts[0] = "52"
                    new_lines.append(" ".join(parts))
                    count_52 += 1
                elif cls_id == 36:  # red joker in batman_cards
                    parts[0] = "53"
                    new_lines.append(" ".join(parts))
                    count_53 += 1

            if new_lines:
                base_name = os.path.basename(lbl_file)
                dest_lbl = os.path.join(dest_lbl_dir, f"batman_joker_{base_name}")
                with open(dest_lbl, "w") as f:
                    f.write("\n".join(new_lines) + "\n")

                img_name = base_name.replace(".txt", ".jpg")
                src_img = os.path.join(img_dir, img_name)
                dest_img = os.path.join(dest_img_dir, f"batman_joker_{img_name}")
                if os.path.exists(src_img):
                    shutil.copy(src_img, dest_img)

    print(f"batman_cards merged: {count_52} Black Jokers (52), {count_53} Red Jokers (53)")
    return count_52, count_53

def classify_joker_crop(crop):
    """Returns 53 if crop is Red Joker (has red/color), 52 if Black Joker (monochrome)."""
    if crop.size == 0:
        return 52
    hsv = cv2.cvtColor(crop, cv2.COLOR_BGR2HSV)
    sat = hsv[:, :, 1]
    val = hsv[:, :, 2]
    hue = hsv[:, :, 0]

    # Red mask: Hue around 0 or 180, high saturation and value
    red_mask = ((hue < 15) | (hue > 165)) & (sat > 60) & (val > 50)
    red_pct = np.mean(red_mask)

    # General color saturation (non-grayscale)
    color_mask = (sat > 60) & (val > 50)
    color_pct = np.mean(color_mask)
    mean_sat = np.mean(sat)

    # Deciding threshold
    if red_pct > 0.015 or (color_pct > 0.08 and mean_sat > 35.0):
        return 53  # Red Joker
    return 52  # Black Joker

def process_jokers_1(jokers_dir, poker_dir):
    print("\n--- 3. Merging JOKERS-1 with Color-Based Red/Black Classification ---")
    if not os.path.exists(jokers_dir):
        print(f"Directory {jokers_dir} not found. Skipping.")
        return 0, 0

    count_52 = 0
    count_53 = 0

    for split in ["train", "valid"]:
        lbl_dir = os.path.join(jokers_dir, split, "labels")
        img_dir = os.path.join(jokers_dir, split, "images")
        dest_lbl_dir = os.path.join(poker_dir, split, "labels")
        dest_img_dir = os.path.join(poker_dir, split, "images")

        if not os.path.exists(lbl_dir):
            continue

        for lbl_file in glob.glob(os.path.join(lbl_dir, "*.txt")):
            base_name = os.path.basename(lbl_file)
            img_name = base_name.replace(".txt", ".jpg")
            src_img = os.path.join(img_dir, img_name)
            if not os.path.exists(src_img):
                continue

            img = cv2.imread(src_img)
            if img is None:
                continue
            h, w, _ = img.shape

            with open(lbl_file, "r") as f:
                lines = f.readlines()

            boxes = []
            crops = []
            for line in lines:
                parts = line.strip().split()
                if not parts:
                    continue
                xc, yc, bw, bh = map(float, parts[1:5])
                x1 = max(0, int((xc - bw / 2) * w))
                y1 = max(0, int((yc - bh / 2) * h))
                x2 = min(w, int((xc + bw / 2) * w))
                y2 = min(h, int((yc + bh / 2) * h))
                crop = img[y1:y2, x1:x2]
                boxes.append(parts)
                crops.append(crop)

            # If an image contains 2 boxes (a pair of jokers), relative comparison is extremely reliable
            assigned_classes = []
            if len(boxes) == 2 and crops[0].size > 0 and crops[1].size > 0:
                hsv0 = cv2.cvtColor(crops[0], cv2.COLOR_BGR2HSV)
                hsv1 = cv2.cvtColor(crops[1], cv2.COLOR_BGR2HSV)
                red0 = np.mean(((hsv0[:,:,0] < 15) | (hsv0[:,:,0] > 165)) & (hsv0[:,:,1] > 60) & (hsv0[:,:,2] > 50))
                red1 = np.mean(((hsv1[:,:,0] < 15) | (hsv1[:,:,0] > 165)) & (hsv1[:,:,1] > 60) & (hsv1[:,:,2] > 50))
                sat0 = np.mean(hsv0[:,:,1])
                sat1 = np.mean(hsv1[:,:,1])

                if red0 > red1 + 0.01 or sat0 > sat1 + 15.0:
                    assigned_classes = [53, 52]
                elif red1 > red0 + 0.01 or sat1 > sat0 + 15.0:
                    assigned_classes = [52, 53]
                else:
                    assigned_classes = [classify_joker_crop(crops[0]), classify_joker_crop(crops[1])]
            else:
                for c in crops:
                    assigned_classes.append(classify_joker_crop(c))

            new_lines = []
            for parts, cls_id in zip(boxes, assigned_classes):
                parts[0] = str(cls_id)
                new_lines.append(" ".join(parts))
                if cls_id == 52:
                    count_52 += 1
                else:
                    count_53 += 1

            dest_lbl = os.path.join(dest_lbl_dir, f"joker1_{base_name}")
            with open(dest_lbl, "w") as f:
                f.write("\n".join(new_lines) + "\n")

            dest_img = os.path.join(dest_img_dir, f"joker1_{img_name}")
            shutil.copy(src_img, dest_img)

    print(f"JOKERS-1 merged: {count_52} Black Jokers (52), {count_53} Red Jokers (53)")
    return count_52, count_53

def generate_synthetic_jokers(templates_dir, poker_dir, num_samples_each=120):
    print("\n--- 4. Synthesizing Augmented Jokers from Templates ---")
    black_template_path = os.path.join(templates_dir, "black_joker.png")
    red_template_path = os.path.join(templates_dir, "red_joker.png")

    if not os.path.exists(black_template_path) or not os.path.exists(red_template_path):
        print("Templates not found. Skipping synthesis.")
        return 0, 0

    black_tpl = cv2.imread(black_template_path, cv2.IMREAD_UNCHANGED)
    red_tpl = cv2.imread(red_template_path, cv2.IMREAD_UNCHANGED)

    # Base background textures (table surfaces, green mats, gradients)
    def create_background(w, h):
        bg_type = random.choice(["wood", "green_felt", "desk", "gray"])
        if bg_type == "green_felt":
            bg = np.zeros((h, w, 3), dtype=np.uint8)
            bg[:, :] = [random.randint(20, 50), random.randint(70, 130), random.randint(20, 50)]
        elif bg_type == "wood":
            bg = np.zeros((h, w, 3), dtype=np.uint8)
            bg[:, :] = [random.randint(20, 45), random.randint(45, 90), random.randint(80, 140)]
        elif bg_type == "desk":
            bg = np.zeros((h, w, 3), dtype=np.uint8)
            c = random.randint(180, 230)
            bg[:, :] = [c, c - 10, c - 20]
        else:
            c = random.randint(60, 180)
            bg = np.full((h, w, 3), c, dtype=np.uint8)

        # Add subtle noise
        noise = np.random.normal(0, 10, bg.shape).astype(np.int16)
        bg = np.clip(bg.astype(np.int16) + noise, 0, 255).astype(np.uint8)
        return bg

    def augment_and_place(card_img, bg, class_id):
        # Resize card to occupy ~25% to ~65% of screen height
        bg_h, bg_w, _ = bg.shape
        scale = random.uniform(0.35, 0.70)
        card_h = int(bg_h * scale)
        card_w = int(card_h * (card_img.shape[1] / card_img.shape[0]))
        resized_card = cv2.resize(card_img, (card_w, card_h), interpolation=cv2.INTER_AREA)

        # Rotation
        angle = random.uniform(-35, 35)
        M = cv2.getRotationMatrix2D((card_w / 2, card_h / 2), angle, 1.0)
        cos = np.abs(M[0, 0])
        sin = np.abs(M[0, 1])
        new_w = int((card_h * sin) + (card_w * cos))
        new_h = int((card_h * cos) + (card_w * sin))
        M[0, 2] += (new_w / 2) - card_w / 2
        M[1, 2] += (new_h / 2) - card_h / 2

        rotated_card = cv2.warpAffine(resized_card, M, (new_w, new_h), flags=cv2.INTER_LINEAR, borderValue=(0, 0, 0, 0))

        # Position randomly in background
        if bg_w <= new_w or bg_h <= new_h:
            return None, None

        max_x = bg_w - new_w
        max_y = bg_h - new_h
        pos_x = random.randint(0, max_x)
        pos_y = random.randint(0, max_y)

        # Alpha blend or place
        alpha = rotated_card[:, :, 3] / 255.0 if rotated_card.shape[2] == 4 else np.ones((new_h, new_w))
        bgr = rotated_card[:, :, :3]

        # Brightness / Contrast jitter
        alpha_mult = random.uniform(0.8, 1.2)
        beta_add = random.randint(-20, 20)
        bgr = np.clip(bgr.astype(np.int16) * alpha_mult + beta_add, 0, 255).astype(np.uint8)

        for c in range(3):
            bg[pos_y:pos_y+new_h, pos_x:pos_x+new_w, c] = (
                alpha * bgr[:, :, c] + (1 - alpha) * bg[pos_y:pos_y+new_h, pos_x:pos_x+new_w, c]
            )

        # YOLO coordinates (normalized xc, yc, bw, bh)
        xc = (pos_x + new_w / 2) / bg_w
        yc = (pos_y + new_h / 2) / bg_h
        bw = (new_w * 0.95) / bg_w
        bh = (new_h * 0.95) / bg_h

        return bg, f"{class_id} {xc:.6f} {yc:.6f} {bw:.6f} {bh:.6f}"

    count_52 = 0
    count_53 = 0

    for i in range(num_samples_each):
        split = "train" if random.random() < 0.85 else "valid"
        dest_lbl_dir = os.path.join(poker_dir, split, "labels")
        dest_img_dir = os.path.join(poker_dir, split, "images")

        # 1. Black Joker (52)
        bg = create_background(640, 640)
        res_img, label_line = augment_and_place(black_tpl, bg, 52)
        if res_img is not None:
            name = f"synth_joker_black_{i:04d}"
            cv2.imwrite(os.path.join(dest_img_dir, f"{name}.jpg"), res_img)
            with open(os.path.join(dest_lbl_dir, f"{name}.txt"), "w") as f:
                f.write(label_line + "\n")
            count_52 += 1

        # 2. Red Joker (53)
        bg = create_background(640, 640)
        res_img, label_line = augment_and_place(red_tpl, bg, 53)
        if res_img is not None:
            name = f"synth_joker_red_{i:04d}"
            cv2.imwrite(os.path.join(dest_img_dir, f"{name}.jpg"), res_img)
            with open(os.path.join(dest_lbl_dir, f"{name}.txt"), "w") as f:
                f.write(label_line + "\n")
            count_53 += 1

    print(f"Synthesized: {count_52} Black Jokers (52), {count_53} Red Jokers (53)")
    return count_52, count_53

def update_yaml_and_verify(poker_dir):
    print("\n--- 5. Updating data.yaml and Verifying Dataset ---")
    yaml_path = os.path.join(poker_dir, "data.yaml")
    with open(yaml_path, "r") as f:
        data = yaml.safe_load(f)

    data["nc"] = 54
    if len(data["names"]) == 52:
        data["names"].append("Black_Joker")
        data["names"].append("Red_Joker")
    elif len(data["names"]) == 54:
        data["names"][52] = "Black_Joker"
        data["names"][53] = "Red_Joker"

    with open(yaml_path, "w") as f:
        yaml.safe_dump(data, f)

    # Count all classes across splits
    split_counts = {}
    for split in ["train", "valid", "test"]:
        lbl_dir = os.path.join(poker_dir, split, "labels")
        c52 = 0
        c53 = 0
        total_files = 0
        for f in glob.glob(os.path.join(lbl_dir, "*.txt")):
            total_files += 1
            for line in open(f):
                parts = line.strip().split()
                if not parts:
                    continue
                c = int(parts[0])
                if c == 52:
                    c52 += 1
                elif c == 53:
                    c53 += 1
        split_counts[split] = (total_files, c52, c53)

    print("\nDataset Verification Summary:")
    print(f"  Class 52: {data['names'][52]}")
    print(f"  Class 53: {data['names'][53]}")
    for split, (total, c52, c53) in split_counts.items():
        print(f"  [{split.upper()}] Files: {total} | Black_Joker (52): {c52} | Red_Joker (53): {c53}")

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    poker_dir = os.path.join(root_dir, "training", "poker--2")
    batman_dir = os.path.join(root_dir, "training", "batman_cards")
    jokers_dir = os.path.join(root_dir, "JOKERS-1")
    templates_dir = os.path.join(root_dir, "training", "dataset_v1")

    remove_old_joker_data(poker_dir)
    process_batman_cards(batman_dir, poker_dir)
    process_jokers_1(jokers_dir, poker_dir)
    generate_synthetic_jokers(templates_dir, poker_dir, num_samples_each=120)
    update_yaml_and_verify(poker_dir)
    print("\nAll Done! Step 1 completed successfully.")

if __name__ == "__main__":
    main()
