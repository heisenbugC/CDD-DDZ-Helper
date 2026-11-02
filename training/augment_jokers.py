"""
Automated Data Augmentation Engine for Joker Cards (DDZ / CDD).
Solves the class imbalance and scale mismatch between 52 standard cards and Jokers:
- Synthesizes diverse spatial variations (rotation, 3D perspective warp, scale).
- Generates both Full-Card scenes and Corner-Pip zooms (matching live phone scanner anchor scale).
- Strictly preserves Red Joker chroma (prevents hue drift or desaturation).
- Automatically balances Class 52 (Black Joker) and Class 53 (Red Joker) to ~1000+ annotations.
- Integrates directly into training/poker--2 and saves visual verification grid.
"""

import os
import sys
import glob
import math
import random
import shutil
import argparse
import yaml
import cv2
import numpy as np
from tqdm import tqdm

def parse_args():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    parser = argparse.ArgumentParser(description="Auto-augment Joker cards for DDZ/CDD.")
    parser.add_argument("--source_dir", type=str, default=os.path.join(root, "training", "roboflow_jokers"),
                        help="Path to Roboflow Joker dataset")
    parser.add_argument("--poker_dir", type=str, default=os.path.join(root, "training", "poker--2"),
                        help="Path to poker--2 dataset")
    parser.add_argument("--target_train_count", type=int, default=1100,
                        help="Target number of annotations per Joker class for training")
    parser.add_argument("--target_val_count", type=int, default=150,
                        help="Target number of annotations per Joker class for validation")
    parser.add_argument("--img_size", type=int, default=640,
                        help="Output image size (width & height)")
    parser.add_argument("--strip_old", action="store_true", default=True,
                        help="Strip existing old joker files from poker--2 before integrating")
    parser.add_argument("--preview_only", action="store_true",
                        help="Only generate 16 sample images and preview grid without writing to poker--2")
    return parser.parse_args()

def parse_roboflow_classes(rf_yaml_path):
    if not os.path.exists(rf_yaml_path):
        return None, None
    with open(rf_yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    names = data.get("names", [])
    if isinstance(names, dict):
        names = [names[k] for k in sorted(names.keys())]

    black_idx, red_idx = None, None
    for idx, name in enumerate(names):
        norm = name.lower().replace("-", "_").replace(" ", "_")
        if any(k in norm for k in ["black", "hei", "xiao", "small", "mono"]):
            black_idx = idx
        elif any(k in norm for k in ["red", "hong", "da", "big", "color"]):
            red_idx = idx

    # Fallback to 0: Black, 1: Red if names match
    if black_idx is None and len(names) >= 2:
        black_idx = 0
    if red_idx is None and len(names) >= 2:
        red_idx = 1

    return black_idx, red_idx

def strip_old_joker_data(poker_dir):
    print("\n" + "=" * 65)
    print("STEP 1: Purging old Joker data from poker--2")
    print("=" * 65)
    deleted_lbls = 0
    deleted_imgs = 0
    cleaned_lbls = 0

    for split in ["train", "valid", "test"]:
        lbl_dir = os.path.join(poker_dir, split, "labels")
        img_dir = os.path.join(poker_dir, split, "images")
        if not os.path.exists(lbl_dir):
            continue

        for lbl_path in glob.glob(os.path.join(lbl_dir, "*")):
            base = os.path.basename(lbl_path).lower()
            if "joker" in base:
                os.remove(lbl_path)
                deleted_lbls += 1
            else:
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
        print(f"  [OK] Cleaned {cleaned_lbls} files with lingering joker lines.")

class JokerSeed:
    """Represents an extracted Joker card instance with its full crop and corner crops."""
    def __init__(self, card_img, class_id):
        self.card_img = card_img      # BGR uint8 image of the card
        self.class_id = class_id      # 52 (Black) or 53 (Red)
        h, w = card_img.shape[:2]

        # Extract top-left corner crop (~35% width, ~42% height)
        c_w = max(10, int(w * 0.38))
        c_h = max(10, int(h * 0.45))
        self.corner_tl = card_img[0:c_h, 0:c_w].copy()

        # Extract bottom-right corner crop and rotate 180 deg to upright
        self.corner_br = cv2.rotate(card_img[h - c_h:h, w - c_w:w], cv2.ROTATE_180)

def extract_seeds(source_dir, black_idx, red_idx):
    print("\n" + "=" * 65)
    print("STEP 2: Extracting seed instances from Roboflow dataset")
    print("=" * 65)
    img_files = glob.glob(os.path.join(source_dir, "**", "*.jpg"), recursive=True) + \
                glob.glob(os.path.join(source_dir, "**", "*.png"), recursive=True)

    seeds_black = []
    seeds_red = []

    for img_path in img_files:
        lbl_path = img_path.replace("images", "labels").rsplit(".", 1)[0] + ".txt"
        if not os.path.exists(lbl_path):
            continue
        img = cv2.imread(img_path)
        if img is None:
            continue
        h_img, w_img = img.shape[:2]

        with open(lbl_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        for line in lines:
            parts = line.strip().split()
            if len(parts) < 5:
                continue
            cls_id = int(parts[0])
            cx = float(parts[1]) * w_img
            cy = float(parts[2]) * h_img
            bw = float(parts[3]) * w_img
            bh = float(parts[4]) * h_img

            x0 = max(0, int(cx - bw / 2.0))
            y0 = max(0, int(cy - bh / 2.0))
            x1 = min(w_img - 1, int(cx + bw / 2.0))
            y1 = min(h_img - 1, int(cy + bh / 2.0))

            if (x1 - x0) < 30 or (y1 - y0) < 30:
                continue

            card_crop = img[y0:y1, x0:x1]
            if cls_id == black_idx:
                seeds_black.append(JokerSeed(card_crop, 52))
            elif cls_id == red_idx:
                seeds_red.append(JokerSeed(card_crop, 53))

    print(f"  [OK] Extracted {len(seeds_black)} Black Joker seeds and {len(seeds_red)} Red Joker seeds.")
    return seeds_black, seeds_red

def create_procedural_background(width, height):
    """Creates a realistic card table background (green felt, wood grain, or slate)."""
    bg_type = random.choice(["felt_green", "wood", "dark_felt", "light_neutral"])
    if bg_type == "felt_green":
        # Green poker felt with noise
        base = np.full((height, width, 3), (35, 95, 45), dtype=np.uint8)
        noise = np.random.randint(-12, 12, (height, width, 3), dtype=np.int16)
        bg = np.clip(base.astype(np.int16) + noise, 0, 255).astype(np.uint8)
    elif bg_type == "wood":
        # Warm wood tone with horizontal grain
        base = np.full((height, width, 3), (30, 60, 110), dtype=np.uint8)
        grain = np.random.randint(-18, 18, (height, 1, 3), dtype=np.int16)
        bg = np.clip(base.astype(np.int16) + grain, 0, 255).astype(np.uint8)
    elif bg_type == "dark_felt":
        # Dark blue/navy casino felt
        base = np.full((height, width, 3), (85, 45, 30), dtype=np.uint8)
        noise = np.random.randint(-10, 10, (height, width, 3), dtype=np.int16)
        bg = np.clip(base.astype(np.int16) + noise, 0, 255).astype(np.uint8)
    else:
        # Off-white / light tabletop
        base = np.full((height, width, 3), (210, 215, 215), dtype=np.uint8)
        noise = np.random.randint(-15, 15, (height, width, 3), dtype=np.int16)
        bg = np.clip(base.astype(np.int16) + noise, 0, 255).astype(np.uint8)
    return bg

def get_random_background(poker_train_dir, width=640, height=640):
    """Samples a real card table background crop from poker--2, or procedural background."""
    if random.random() < 0.65 and os.path.exists(poker_train_dir):
        img_candidates = glob.glob(os.path.join(poker_train_dir, "*.jpg"))
        if img_candidates:
            bg_path = random.choice(img_candidates)
            bg_img = cv2.imread(bg_path)
            if bg_img is not None and bg_img.shape[0] >= height and bg_img.shape[1] >= width:
                # Random crop
                max_y = bg_img.shape[0] - height
                max_x = bg_img.shape[1] - width
                y0 = random.randint(0, max_y)
                x0 = random.randint(0, max_x)
                return bg_img[y0:y0 + height, x0:x0 + width].copy()
    return create_procedural_background(width, height)

def apply_color_safe_jitter(patch, is_red):
    """
    Applies photometric adjustments while strictly protecting Red Joker hue and saturation.
    """
    img = patch.copy()
    # 1. Brightness & Contrast
    alpha = random.uniform(0.80, 1.25)  # Contrast
    beta = random.randint(-25, 25)      # Brightness
    img = np.clip(alpha * img.astype(np.float32) + beta, 0, 255).astype(np.uint8)

    # 2. HSV tuning
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
    h, s, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]

    if is_red:
        # Boost or maintain saturation for Red Joker; never desaturate
        sat_mult = random.uniform(0.95, 1.30)
        s = np.clip(s * sat_mult, 0, 255)
        # Minor hue shift within red bounds (at most +- 3 deg)
        h_shift = random.uniform(-3.0, 3.0)
        h = np.mod(h + h_shift, 180.0)
    else:
        # Keep Black Joker grayscale/low-saturation
        s = np.clip(s * 0.70, 0, 45)

    hsv[:, :, 0] = h
    hsv[:, :, 1] = s
    hsv[:, :, 2] = v
    img = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

    # 3. Optional slight blur (simulates camera focus / motion)
    if random.random() < 0.35:
        ksize = random.choice([3, 5])
        img = cv2.GaussianBlur(img, (ksize, ksize), 0)

    return img

def transform_patch_and_box(patch, target_w, target_h, angle, perspective_jitter=0.15):
    """
    Resizes patch and applies 3D perspective distortion + rotation.
    Returns warped patch, binary mask, and the 4 warped corner points.
    """
    resized = cv2.resize(patch, (target_w, target_h), interpolation=cv2.INTER_LINEAR)
    src_pts = np.float32([
        [0, 0],
        [target_w - 1, 0],
        [target_w - 1, target_h - 1],
        [0, target_h - 1]
    ])

    # 3D perspective displacement
    max_dx = target_w * perspective_jitter
    max_dy = target_h * perspective_jitter

    dst_pts = np.float32([
        [random.uniform(-max_dx, max_dx), random.uniform(-max_dy, max_dy)],
        [target_w - 1 + random.uniform(-max_dx, max_dx), random.uniform(-max_dy, max_dy)],
        [target_w - 1 + random.uniform(-max_dx, max_dx), target_h - 1 + random.uniform(-max_dy, max_dy)],
        [random.uniform(-max_dx, max_dx), target_h - 1 + random.uniform(-max_dy, max_dy)]
    ])

    # Homography matrix
    H = cv2.getPerspectiveTransform(src_pts, dst_pts)

    # Affine rotation matrix centered at patch center
    center = (target_w / 2.0, target_h / 2.0)
    R = cv2.getRotationMatrix2D(center, angle, 1.0)
    R_3x3 = np.eye(3, dtype=np.float32)
    R_3x3[:2, :] = R

    # Combined transform M = R * H
    M = np.dot(R_3x3, H)

    # Calculate bounding rect of warped corners
    corners = np.array([
        [0, 0, 1],
        [target_w - 1, 0, 1],
        [target_w - 1, target_h - 1, 1],
        [0, target_h - 1, 1]
    ], dtype=np.float32).T

    warped_corners = np.dot(M, corners)
    warped_corners /= (warped_corners[2:3, :] + 1e-7)
    xs = warped_corners[0, :]
    ys = warped_corners[1, :]

    min_x, max_x = np.min(xs), np.max(xs)
    min_y, max_y = np.min(ys), np.max(ys)

    # Translation to keep patch positive
    out_w = int(math.ceil(max_x - min_x)) + 4
    out_h = int(math.ceil(max_y - min_y)) + 4
    T = np.array([[1, 0, -min_x + 2], [0, 1, -min_y + 2], [0, 0, 1]], dtype=np.float32)
    M_final = np.dot(T, M)

    warped_patch = cv2.warpPerspective(resized, M_final, (out_w, out_h), borderValue=(0, 0, 0))
    mask = cv2.warpPerspective(np.ones((target_h, target_w), dtype=np.uint8) * 255,
                               M_final, (out_w, out_h), borderValue=0)

    # Recompute corner coordinates relative to warped_patch
    final_corners = np.dot(M_final, corners)
    final_corners /= (final_corners[2:3, :] + 1e-7)
    box_min_x = np.min(final_corners[0, :])
    box_max_x = np.max(final_corners[0, :])
    box_min_y = np.min(final_corners[1, :])
    box_max_y = np.max(final_corners[1, :])

    return warped_patch, mask, (box_min_x, box_min_y, box_max_x, box_max_y)

def generate_augmented_image(seeds_list, bg_img, mode="corner_and_card", img_size=640):
    """
    Pastes 1 to 3 Joker cards or corner crops onto the background canvas.
    Returns: canvas (BGR), list of (class_id, norm_cx, norm_cy, norm_w, norm_h).
    """
    canvas = bg_img.copy()
    canvas_h, canvas_w = canvas.shape[:2]
    annotations = []

    # Pick 1 or 2 cards to place
    num_items = random.choices([1, 2], weights=[0.45, 0.55])[0]
    chosen_seeds = random.sample(seeds_list, min(num_items, len(seeds_list)))

    for seed in chosen_seeds:
        is_red = (seed.class_id == 53)

        # Decide whether to place full card or corner pip
        use_corner = (random.random() < 0.60) if mode == "corner_and_card" else (mode == "corner_only")

        if use_corner:
            patch = random.choice([seed.corner_tl, seed.corner_br])
            # Target size on 640x640 matching poker--2 hand corner pips: width 35..85 px (norm 0.05..0.13)
            target_w = random.randint(35, 85)
            target_h = int(target_w * random.uniform(1.20, 1.65))
            angle = random.uniform(-20.0, 20.0)
            jitter = random.uniform(0.06, 0.14)
        else:
            patch = seed.card_img
            # Full card target size on 640x640: width 160..300 px
            target_w = random.randint(160, 300)
            target_h = int(target_w * random.uniform(1.35, 1.55))
            angle = random.uniform(-35.0, 35.0) if random.random() < 0.85 else random.uniform(145.0, 215.0)
            jitter = random.uniform(0.08, 0.18)

        # Apply color-safe jitter
        jittered_patch = apply_color_safe_jitter(patch, is_red)

        # Warp patch
        warped_patch, mask, (bx0, by0, bx1, by1) = transform_patch_and_box(
            jittered_patch, target_w, target_h, angle, jitter
        )

        pw, ph = warped_patch.shape[1], warped_patch.shape[0]
        if pw >= canvas_w or ph >= canvas_h:
            continue

        # Choose placement coordinates
        max_px = canvas_w - pw
        max_py = canvas_h - ph
        pos_x = random.randint(0, max_px)
        pos_y = random.randint(0, max_py)

        # Alpha composite warped patch onto canvas
        mask_3ch = cv2.merge([mask, mask, mask]) / 255.0
        roi = canvas[pos_y:pos_y + ph, pos_x:pos_x + pw]
        blended = (warped_patch * mask_3ch + roi * (1.0 - mask_3ch)).astype(np.uint8)
        canvas[pos_y:pos_y + ph, pos_x:pos_x + pw] = blended

        # Calculate bounding box in normalized canvas coordinates
        abs_x0 = max(0.0, pos_x + bx0)
        abs_y0 = max(0.0, pos_y + by0)
        abs_x1 = min(float(canvas_w), pos_x + bx1)
        abs_y1 = min(float(canvas_h), pos_y + by1)

        box_w = abs_x1 - abs_x0
        box_h = abs_y1 - abs_y0

        if box_w >= 15 and box_h >= 20:
            norm_cx = (abs_x0 + abs_x1) / (2.0 * canvas_w)
            norm_cy = (abs_y0 + abs_y1) / (2.0 * canvas_h)
            norm_w = box_w / float(canvas_w)
            norm_h = box_h / float(canvas_h)
            annotations.append((seed.class_id, norm_cx, norm_cy, norm_w, norm_h))

    return canvas, annotations

def draw_preview_grid(sample_images, sample_annotations, out_path="augment_preview.jpg"):
    """Creates a 4x4 collage with labeled bounding boxes for visual verification."""
    grid_rows = 4
    grid_cols = 4
    cell_w, cell_h = 320, 320
    canvas = np.zeros((grid_rows * cell_h, grid_cols * cell_w, 3), dtype=np.uint8)

    for i in range(min(16, len(sample_images))):
        img = sample_images[i].copy()
        annos = sample_annotations[i]
        h_orig, w_orig = img.shape[:2]

        for cls_id, n_cx, n_cy, n_w, n_h in annos:
            x0 = int((n_cx - n_w / 2.0) * w_orig)
            y0 = int((n_cy - n_h / 2.0) * h_orig)
            x1 = int((n_cx + n_w / 2.0) * w_orig)
            y1 = int((n_cy + n_h / 2.0) * h_orig)

            color = (0, 0, 240) if cls_id == 53 else (220, 120, 20)  # Red vs Blue
            label = "53: Red_Joker" if cls_id == 53 else "52: Black_Joker"

            cv2.rectangle(img, (x0, y0), (x1, y1), color, 2)
            cv2.putText(img, label, (x0, max(18, y0 - 6)), cv2.FONT_HERSHEY_SIMPLEX, 0.55, color, 2)

        thumb = cv2.resize(img, (cell_w, cell_h))
        r = i // grid_cols
        c = i % grid_cols
        canvas[r * cell_h:(r + 1) * cell_h, c * cell_w:(c + 1) * cell_w] = thumb

    cv2.imwrite(out_path, canvas)
    print(f"\n[OK] Visual inspection grid saved to: {out_path}")

def update_poker_data_yaml(poker_yaml_path):
    with open(poker_yaml_path, "r", encoding="utf-8") as f:
        poker_data = yaml.safe_load(f)

    poker_data["nc"] = 54
    poker_names = poker_data.get("names", [])
    while len(poker_names) < 52:
        poker_names.append(str(len(poker_names)))
    if len(poker_names) == 52:
        poker_names.append("Black_Joker")
        poker_names.append("Red_Joker")
    elif len(poker_names) >= 54:
        poker_names[52] = "Black_Joker"
        poker_names[53] = "Red_Joker"

    poker_data["names"] = poker_names
    with open(poker_yaml_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(poker_data, f, sort_keys=False)
    print(f"  [OK] Updated {poker_yaml_path} with nc=54 and Class 52/53 Joker names.")

def main():
    args = parse_args()
    print("=" * 65)
    print("      DDZ/CDD Joker Automatic Augmentation Pipeline      ")
    print("=" * 65)

    rf_yaml = os.path.join(args.source_dir, "data.yaml")
    black_idx, red_idx = parse_roboflow_classes(rf_yaml)
    if black_idx is None or red_idx is None:
        print(f"ERROR: Could not parse classes from {rf_yaml}")
        sys.exit(1)

    print(f"Roboflow class mapping: Class {black_idx} -> 52 (Black Joker), Class {red_idx} -> 53 (Red Joker)")

    # 1. Purge old jokers from poker--2
    if args.strip_old and not args.preview_only:
        strip_old_joker_data(args.poker_dir)

    # 2. Extract seed instances
    seeds_black, seeds_red = extract_seeds(args.source_dir, black_idx, red_idx)
    if not seeds_black or not seeds_red:
        print("ERROR: Insufficient seeds extracted.")
        sys.exit(1)

    all_seeds = seeds_black + seeds_red

    # 3. Setup output directories
    poker_train_img_dir = os.path.join(args.poker_dir, "train", "images")
    poker_train_lbl_dir = os.path.join(args.poker_dir, "train", "labels")
    poker_val_img_dir = os.path.join(args.poker_dir, "valid", "images")
    poker_val_lbl_dir = os.path.join(args.poker_dir, "valid", "labels")

    os.makedirs(poker_train_img_dir, exist_ok=True)
    os.makedirs(poker_train_lbl_dir, exist_ok=True)
    os.makedirs(poker_val_img_dir, exist_ok=True)
    os.makedirs(poker_val_lbl_dir, exist_ok=True)

    # 4. Generate Training Set
    print("\n" + "=" * 65)
    print(f"STEP 3: Generating balanced training dataset (Target: {args.target_train_count} per class)")
    print("=" * 65)

    c52_count = 0
    c53_count = 0
    train_idx = 0
    preview_images = []
    preview_annos = []

    pbar = tqdm(total=args.target_train_count * 2, desc="Generating Train Samples")

    while (c52_count < args.target_train_count or c53_count < args.target_train_count):
        # Sample selection to balance classes
        if c52_count < c53_count:
            active_pool = seeds_black
        elif c53_count < c52_count:
            active_pool = seeds_red
        else:
            active_pool = all_seeds

        bg = get_random_background(poker_train_img_dir, args.img_size, args.img_size)
        aug_img, annos = generate_augmented_image(active_pool, bg, mode="corner_and_card", img_size=args.img_size)

        if not annos:
            continue

        for cls_id, _, _, _, _ in annos:
            if cls_id == 52:
                c52_count += 1
                pbar.update(1)
            elif cls_id == 53:
                c53_count += 1
                pbar.update(1)

        if len(preview_images) < 16:
            preview_images.append(aug_img)
            preview_annos.append(annos)

        if not args.preview_only:
            stem = f"aug_joker_train_{train_idx:05d}"
            img_out = os.path.join(poker_train_img_dir, f"{stem}.jpg")
            lbl_out = os.path.join(poker_train_lbl_dir, f"{stem}.txt")

            cv2.imwrite(img_out, aug_img, [cv2.IMWRITE_JPEG_QUALITY, 95])
            with open(lbl_out, "w", encoding="utf-8") as f:
                for cls_id, cx, cy, bw, bh in annos:
                    f.write(f"{cls_id} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")

        train_idx += 1

    pbar.close()
    print(f"  [OK] Train set generated: {train_idx} images (Class 52: {c52_count}, Class 53: {c53_count})")

    # 5. Generate Validation Set
    print("\n" + "=" * 65)
    print(f"STEP 4: Generating validation dataset (Target: {args.target_val_count} per class)")
    print("=" * 65)

    v52_count = 0
    v53_count = 0
    val_idx = 0
    val_pbar = tqdm(total=args.target_val_count * 2, desc="Generating Val Samples")

    while (v52_count < args.target_val_count or v53_count < args.target_val_count):
        if v52_count < v53_count:
            active_pool = seeds_black
        elif v53_count < v52_count:
            active_pool = seeds_red
        else:
            active_pool = all_seeds

        bg = get_random_background(poker_train_img_dir, args.img_size, args.img_size)
        aug_img, annos = generate_augmented_image(active_pool, bg, mode="corner_and_card", img_size=args.img_size)

        if not annos:
            continue

        for cls_id, _, _, _, _ in annos:
            if cls_id == 52:
                v52_count += 1
                val_pbar.update(1)
            elif cls_id == 53:
                v53_count += 1
                val_pbar.update(1)

        if not args.preview_only:
            stem = f"aug_joker_val_{val_idx:05d}"
            img_out = os.path.join(poker_val_img_dir, f"{stem}.jpg")
            lbl_out = os.path.join(poker_val_lbl_dir, f"{stem}.txt")

            cv2.imwrite(img_out, aug_img, [cv2.IMWRITE_JPEG_QUALITY, 95])
            with open(lbl_out, "w", encoding="utf-8") as f:
                for cls_id, cx, cy, bw, bh in annos:
                    f.write(f"{cls_id} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")

        val_idx += 1

    val_pbar.close()
    print(f"  [OK] Val set generated: {val_idx} images (Class 52: {v52_count}, Class 53: {v53_count})")

    # 6. Save visual verification preview
    preview_file = os.path.join(os.path.dirname(__file__), "augment_preview.jpg")
    draw_preview_grid(preview_images, preview_annos, preview_file)

    # 7. Update data.yaml
    if not args.preview_only:
        poker_yaml = os.path.join(args.poker_dir, "data.yaml")
        update_poker_data_yaml(poker_yaml)

    print("\n" + "=" * 65)
    print("SUCCESS: Automatic augmentation and dataset balancing completed!")
    print(f"Preview grid: {preview_file}")
    print("=" * 65)

if __name__ == "__main__":
    main()

