"""
Advanced Data Augmentation Engine for Joker Cards (DDZ / CDD).
Solves the class imbalance, scale mismatch, AND multi-card in-hand occlusion:
1. In-Hand CutMix: Injects Jokers directly into real multi-card hand photos from poker--2.
2. Synthetic Fanned Hand: Synthesizes 5 to 14 card fanned hands with overlapping cards (~70% occlusion).
3. Tabletop Isolated Card: Retains flat/perspective card samples for table scanning.
4. Red Chroma Preservation: Strictly locks Red Joker ink hue (no orange/brown drift) and saturation.
5. Balances Class 52 (Black Joker) and Class 53 (Red Joker) to ~1100+ annotations.
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
    parser = argparse.ArgumentParser(description="Auto-augment Joker cards with in-hand occlusion for DDZ/CDD.")
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
                try:
                    os.remove(lbl_path)
                    deleted_lbls += 1
                except OSError:
                    pass
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
                    try:
                        os.remove(img_path)
                        deleted_imgs += 1
                    except OSError:
                        pass

    print(f"  [OK] Deleted {deleted_lbls} old joker label files.")
    print(f"  [OK] Deleted {deleted_imgs} old joker image files.")
    if cleaned_lbls > 0:
        print(f"  [OK] Cleaned {cleaned_lbls} files with lingering joker lines.")

class JokerSeed:
    """Represents an extracted Joker card instance with full card and narrow index corner crops."""
    def __init__(self, card_img, class_id):
        self.card_img = card_img      # BGR uint8 image of the card
        self.class_id = class_id      # 52 (Black) or 53 (Red)
        h, w = card_img.shape[:2]

        # Extract top-left corner crop (~36% width, ~42% height)
        c_w = max(10, int(w * 0.38))
        c_h = max(10, int(h * 0.44))
        self.corner_tl = card_img[0:c_h, 0:c_w].copy()

        # Extract bottom-right corner crop and rotate 180 deg
        br_w = max(10, int(w * 0.38))
        br_h = max(10, int(h * 0.44))
        self.corner_br = cv2.rotate(card_img[h - br_h:h, w - br_w:w], cv2.ROTATE_180)

        # Narrow index pip strip (~22% width, ~32% height - typical fanned hand visible corner)
        n_w = max(8, int(w * 0.25))
        n_h = max(12, int(h * 0.35))
        self.index_strip = card_img[0:n_h, 0:n_w].copy()

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

def collect_standard_card_crops(poker_train_dir, max_crops=400):
    """Collects real card corner crops from poker--2 images to use for synthetic fanned hands."""
    img_files = glob.glob(os.path.join(poker_train_dir, "*.jpg"))
    random.shuffle(img_files)
    crops = []
    for img_path in img_files[:250]:
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
            if cls_id >= 52:
                continue
            cx = float(parts[1]) * w_img
            cy = float(parts[2]) * h_img
            bw = float(parts[3]) * w_img
            bh = float(parts[4]) * h_img
            x0 = max(0, int(cx - bw / 2.0))
            y0 = max(0, int(cy - bh / 2.0))
            x1 = min(w_img - 1, int(cx + bw / 2.0))
            y1 = min(h_img - 1, int(cy + bh / 2.0))
            if (x1 - x0) >= 15 and (y1 - y0) >= 15:
                crops.append((img[y0:y1, x0:x1].copy(), cls_id))
                if len(crops) >= max_crops:
                    return crops
    return crops

def create_card_template(w=160, h=240, text="A", suit_symbol="♠", is_red=False):
    """Creates a clean synthetic standard poker card for fanning if needed."""
    card = np.full((h, w, 3), 255, dtype=np.uint8)
    cv2.rectangle(card, (2, 2), (w - 3, h - 3), (220, 220, 220), 1)
    color = (0, 0, 220) if is_red else (20, 20, 20)
    cv2.putText(card, text, (10, 32), cv2.FONT_HERSHEY_DUPLEX, 0.9, color, 2)
    cv2.putText(card, suit_symbol, (12, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.75, color, 2)
    return card

def apply_color_safe_jitter(patch, is_red):
    """
    Photometric adjustments strictly preserving Red Joker ink hue and saturation.
    """
    img = patch.copy()
    alpha = random.uniform(0.85, 1.20)
    beta = random.randint(-20, 20)
    img = np.clip(alpha * img.astype(np.float32) + beta, 0, 255).astype(np.uint8)

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
    h, s, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]

    if is_red:
        sat_mult = random.uniform(0.95, 1.30)
        s = np.clip(s * sat_mult, 0, 255)
        h_shift = random.uniform(-2.5, 2.5)
        h = np.mod(h + h_shift, 180.0)
    else:
        s = np.clip(s * 0.65, 0, 35)

    hsv[:, :, 0] = h
    hsv[:, :, 1] = s
    hsv[:, :, 2] = v
    img = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

    if random.random() < 0.25:
        ksize = random.choice([3, 5])
        img = cv2.GaussianBlur(img, (ksize, ksize), 0)

    return img

def transform_patch_and_box(patch, target_w, target_h, angle, perspective_jitter=0.12):
    """Resizes patch and applies 3D perspective distortion + rotation."""
    resized = cv2.resize(patch, (target_w, target_h), interpolation=cv2.INTER_LINEAR)
    src_pts = np.float32([
        [0, 0],
        [target_w - 1, 0],
        [target_w - 1, target_h - 1],
        [0, target_h - 1]
    ])

    max_dx = target_w * perspective_jitter
    max_dy = target_h * perspective_jitter

    dst_pts = np.float32([
        [random.uniform(-max_dx, max_dx), random.uniform(-max_dy, max_dy)],
        [target_w - 1 + random.uniform(-max_dx, max_dx), random.uniform(-max_dy, max_dy)],
        [target_w - 1 + random.uniform(-max_dx, max_dx), target_h - 1 + random.uniform(-max_dy, max_dy)],
        [random.uniform(-max_dx, max_dx), target_h - 1 + random.uniform(-max_dy, max_dy)]
    ])

    H = cv2.getPerspectiveTransform(src_pts, dst_pts)
    center = (target_w / 2.0, target_h / 2.0)
    R = cv2.getRotationMatrix2D(center, angle, 1.0)
    R_3x3 = np.eye(3, dtype=np.float32)
    R_3x3[:2, :] = R
    M = np.dot(R_3x3, H)

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

    out_w = int(math.ceil(max_x - min_x)) + 4
    out_h = int(math.ceil(max_y - min_y)) + 4
    T = np.array([[1, 0, -min_x + 2], [0, 1, -min_y + 2], [0, 0, 1]], dtype=np.float32)
    M_final = np.dot(T, M)

    warped_patch = cv2.warpPerspective(resized, M_final, (out_w, out_h), borderValue=(0, 0, 0))
    mask = cv2.warpPerspective(np.ones((target_h, target_w), dtype=np.uint8) * 255,
                               M_final, (out_w, out_h), borderValue=0)

    final_corners = np.dot(M_final, corners)
    final_corners /= (final_corners[2:3, :] + 1e-7)
    box_min_x = np.min(final_corners[0, :])
    box_max_x = np.max(final_corners[0, :])
    box_min_y = np.min(final_corners[1, :])
    box_max_y = np.max(final_corners[1, :])

    return warped_patch, mask, (box_min_x, box_min_y, box_max_x, box_max_y)

# ==============================================================================
# 3 Specialized Generation Pipelines
# ==============================================================================

def generate_inhand_cutmix(poker_train_img_dir, target_seed, img_size=640):
    """
    Mode 1: Real Multi-Card In-Hand CutMix (50% probability).
    Picks a real photo of a multi-card hand from poker--2, finds an existing card's
    corner pip, blends the Joker corner pip seamlessly into that slot, and rewrites
    its class to 52/53. This directly bridges the in-hand domain gap!
    """
    img_candidates = glob.glob(os.path.join(poker_train_img_dir, "*.jpg"))
    if not img_candidates:
        return None, []

    base_path = random.choice(img_candidates)
    lbl_path = base_path.replace("images", "labels").rsplit(".", 1)[0] + ".txt"
    if not os.path.exists(lbl_path):
        return None, []

    base_img = cv2.imread(base_path)
    if base_img is None:
        return None, []
    if base_img.shape[0] != img_size or base_img.shape[1] != img_size:
        base_img = cv2.resize(base_img, (img_size, img_size))

    with open(lbl_path, "r", encoding="utf-8") as f:
        lines = [l.strip().split() for l in f if l.strip()]

    if len(lines) < 2:
        return None, []

    # Pick 1 random card in the hand to replace with the Joker
    replace_idx = random.randrange(len(lines))
    target_box = lines[replace_idx]

    cx = float(target_box[1]) * img_size
    cy = float(target_box[2]) * img_size
    bw = float(target_box[3]) * img_size
    bh = float(target_box[4]) * img_size

    x0 = max(0, int(cx - bw / 2.0))
    y0 = max(0, int(cy - bh / 2.0))
    x1 = min(img_size - 1, int(cx + bw / 2.0))
    y1 = min(img_size - 1, int(cy + bh / 2.0))

    crop_w = x1 - x0
    crop_h = y1 - y0
    if crop_w < 15 or crop_h < 15:
        return None, []

    # Select corner crop from the Joker seed
    is_red = (target_seed.class_id == 53)
    joker_corner = random.choice([target_seed.corner_tl, target_seed.corner_br, target_seed.index_strip])
    joker_corner = apply_color_safe_jitter(joker_corner, is_red)
    joker_resized = cv2.resize(joker_corner, (crop_w, crop_h))

    # Soft alpha blend on the border to avoid harsh rectangular seams
    alpha_mask = np.ones((crop_h, crop_w), dtype=np.float32)
    border_px = max(2, min(crop_w, crop_h) // 7)
    for b in range(border_px):
        val = float(b) / float(border_px)
        alpha_mask[b, :] = np.minimum(alpha_mask[b, :], val)
        alpha_mask[crop_h - 1 - b, :] = np.minimum(alpha_mask[crop_h - 1 - b, :], val)
        alpha_mask[:, b] = np.minimum(alpha_mask[:, b], val)
        alpha_mask[:, crop_w - 1 - b] = np.minimum(alpha_mask[:, crop_w - 1 - b], val)

    alpha_3ch = cv2.merge([alpha_mask, alpha_mask, alpha_mask])
    bg_roi = base_img[y0:y1, x0:x1].astype(np.float32)
    fg = joker_resized.astype(np.float32)

    blended = (fg * alpha_3ch + bg_roi * (1.0 - alpha_3ch)).astype(np.uint8)
    canvas = base_img.copy()
    canvas[y0:y1, x0:x1] = blended

    # Assemble updated annotations
    annotations = []
    for i, line in enumerate(lines):
        if i == replace_idx:
            # Replaced with Joker
            norm_cx = (x0 + x1) / (2.0 * img_size)
            norm_cy = (y0 + y1) / (2.0 * img_size)
            norm_w = crop_w / float(img_size)
            norm_h = crop_h / float(img_size)
            annotations.append((target_seed.class_id, norm_cx, norm_cy, norm_w, norm_h))
        else:
            # Original card retained
            cls_id = int(line[0])
            norm_cx = float(line[1])
            norm_cy = float(line[2])
            norm_w = float(line[3])
            norm_h = float(line[4])
            annotations.append((cls_id, norm_cx, norm_cy, norm_w, norm_h))

    return canvas, annotations

def generate_fanned_hand(target_seed, std_card_crops, img_size=640):
    """
    Mode 2: Synthetic Multi-Card Fanned Hand (35% probability).
    Synthesizes a realistic 5 to 14 card fanned hand with genuine 65%~80% occlusion,
    where the Joker is placed at a random position in the fan.
    """
    # Background
    bg_type = random.choice(["felt_green", "wood", "dark_felt", "light_neutral"])
    if bg_type == "felt_green":
        canvas = np.full((img_size, img_size, 3), (35, 95, 45), dtype=np.uint8)
        canvas += np.random.randint(-10, 10, (img_size, img_size, 3), dtype=np.int16).astype(np.uint8)
    elif bg_type == "wood":
        canvas = np.full((img_size, img_size, 3), (30, 60, 110), dtype=np.uint8)
        canvas += np.random.randint(-12, 12, (img_size, 1, 3), dtype=np.int16).astype(np.uint8)
    else:
        canvas = np.full((img_size, img_size, 3), (200, 205, 205), dtype=np.uint8)
        canvas += np.random.randint(-12, 12, (img_size, img_size, 3), dtype=np.int16).astype(np.uint8)

    # Fan parameters
    num_cards = random.randint(5, 13)
    joker_pos = random.randint(0, num_cards - 1)

    # Base card dimensions on 640x640: w=90..130, h=135..190
    card_w = random.randint(90, 125)
    card_h = int(card_w * 1.48)

    # Hand center anchor near lower region
    pivot_x = img_size // 2 + random.randint(-40, 40)
    pivot_y = int(img_size * 0.95) + random.randint(-20, 20)
    radius = random.randint(260, 360)

    # Angular span for the hand: e.g. -25 to +25 deg
    max_half_angle = random.uniform(18.0, 32.0)
    angles = np.linspace(-max_half_angle, max_half_angle, num_cards)

    annotations = []

    # Prepare card surfaces (std cards + 1 joker)
    card_items = []
    for idx in range(num_cards):
        if idx == joker_pos:
            is_red = (target_seed.class_id == 53)
            patch = apply_color_safe_jitter(target_seed.card_img, is_red)
            card_items.append((patch, target_seed.class_id))
        else:
            # Use a std card template
            rank_label = random.choice(["3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"])
            suit_sym = random.choice(["♠", "♥", "♣", "♦"])
            is_r = (suit_sym in ["♥", "♦"])
            t = create_card_template(card_w, card_h, rank_label, suit_sym, is_r)
            card_items.append((t, -1))

    # Render cards back-to-front (left to right)
    for idx in range(num_cards):
        patch, cid = card_items[idx]
        ang = angles[idx]

        rad = math.radians(ang)
        cx = pivot_x + radius * math.sin(rad)
        cy = pivot_y - radius * math.cos(rad)

        # Warp card
        resized = cv2.resize(patch, (card_w, card_h))
        rot_mat = cv2.getRotationMatrix2D((card_w / 2.0, card_h / 2.0), -ang, 1.0)
        warped = cv2.warpAffine(resized, rot_mat, (card_w, card_h), borderValue=(0, 0, 0))
        mask = cv2.warpAffine(np.full((card_h, card_w), 255, dtype=np.uint8), rot_mat, (card_w, card_h), borderValue=0)

        # Place card on canvas
        px0 = int(cx - card_w / 2.0)
        py0 = int(cy - card_h / 2.0)

        # Clipping to canvas
        src_x0 = max(0, -px0)
        src_y0 = max(0, -py0)
        dst_x0 = max(0, px0)
        dst_y0 = max(0, py0)
        dst_x1 = min(img_size, px0 + card_w)
        dst_y1 = min(img_size, py0 + card_h)
        copy_w = dst_x1 - dst_x0
        copy_h = dst_y1 - dst_y0

        if copy_w > 10 and copy_h > 10:
            sub_mask = mask[src_y0:src_y0 + copy_h, src_x0:src_x0 + copy_w]
            sub_warped = warped[src_y0:src_y0 + copy_h, src_x0:src_x0 + copy_w]
            m3 = cv2.merge([sub_mask, sub_mask, sub_mask]) / 255.0

            roi = canvas[dst_y0:dst_y1, dst_x0:dst_x1]
            canvas[dst_y0:dst_y1, dst_x0:dst_x1] = (sub_warped * m3 + roi * (1.0 - m3)).astype(np.uint8)

        # If this is the Joker, annotate its exposed corner pip
        if cid in [52, 53]:
            # The corner index is at the top-left portion of the card
            # Exposed area is roughly ~35% width, ~38% height
            box_w = max(18, int(card_w * 0.36))
            box_h = max(22, int(card_h * 0.38))

            # Center of the top-left corner
            corner_cx = cx - card_w * 0.22
            corner_cy = cy - card_h * 0.24

            norm_cx = max(0.02, min(0.98, corner_cx / float(img_size)))
            norm_cy = max(0.02, min(0.98, corner_cy / float(img_size)))
            norm_w = min(0.20, box_w / float(img_size))
            norm_h = min(0.25, box_h / float(img_size))

            annotations.append((cid, norm_cx, norm_cy, norm_w, norm_h))

    return canvas, annotations

def generate_isolated_card(target_seed, poker_train_dir, img_size=640):
    """
    Mode 3: Tabletop Flat / Perspective Card (15% probability).
    Pastes 1 or 2 cards on a clean background, maintaining capability for single card desk scans.
    """
    bg = np.full((img_size, img_size, 3), (35, 95, 45), dtype=np.uint8)
    if os.path.exists(poker_train_dir):
        imgs = glob.glob(os.path.join(poker_train_dir, "*.jpg"))
        if imgs:
            bg_cand = cv2.imread(random.choice(imgs))
            if bg_cand is not None:
                bg = cv2.resize(bg_cand, (img_size, img_size))

    is_red = (target_seed.class_id == 53)
    target_w = random.randint(140, 240)
    target_h = int(target_w * random.uniform(1.35, 1.55))
    angle = random.uniform(-25.0, 25.0)

    jittered = apply_color_safe_jitter(target_seed.card_img, is_red)
    warped, mask, (bx0, by0, bx1, by1) = transform_patch_and_box(jittered, target_w, target_h, angle, 0.12)

    pw, ph = warped.shape[1], warped.shape[0]
    if pw >= img_size or ph >= img_size:
        return None, []

    pos_x = random.randint(0, img_size - pw)
    pos_y = random.randint(0, img_size - ph)

    m3 = cv2.merge([mask, mask, mask]) / 255.0
    roi = bg[pos_y:pos_y + ph, pos_x:pos_x + pw]
    bg[pos_y:pos_y + ph, pos_x:pos_x + pw] = (warped * m3 + roi * (1.0 - m3)).astype(np.uint8)

    abs_x0 = pos_x + bx0
    abs_y0 = pos_y + by0
    abs_x1 = pos_x + bx1
    abs_y1 = pos_y + by1

    norm_cx = (abs_x0 + abs_x1) / (2.0 * img_size)
    norm_cy = (abs_y0 + abs_y1) / (2.0 * img_size)
    norm_w = (abs_x1 - abs_x0) / float(img_size)
    norm_h = (abs_y1 - abs_y0) / float(img_size)

    return bg, [(target_seed.class_id, norm_cx, norm_cy, norm_w, norm_h)]

# ==============================================================================
# Visualization and Main Pipeline
# ==============================================================================

def draw_preview_grid(sample_images, sample_annotations, out_path="augment_preview.jpg"):
    """Creates a 4x4 collage showing in-hand and fanned Joker bounding boxes."""
    grid_rows, grid_cols = 4, 4
    cell_w, cell_h = 320, 320
    canvas = np.zeros((grid_rows * cell_h, grid_cols * cell_w, 3), dtype=np.uint8)

    for i in range(min(16, len(sample_images))):
        img = sample_images[i].copy()
        annos = sample_annotations[i]
        h_orig, w_orig = img.shape[:2]

        for cls_id, n_cx, n_cy, n_w, n_h in annos:
            if cls_id not in [52, 53]:
                continue
            x0 = int((n_cx - n_w / 2.0) * w_orig)
            y0 = int((n_cy - n_h / 2.0) * h_orig)
            x1 = int((n_cx + n_w / 2.0) * w_orig)
            y1 = int((n_cy + n_h / 2.0) * h_orig)

            color = (0, 0, 245) if cls_id == 53 else (235, 130, 20)
            label = "53: Red_Joker" if cls_id == 53 else "52: Black_Joker"

            cv2.rectangle(img, (x0, y0), (x1, y1), color, 2)
            cv2.putText(img, label, (max(4, x0), max(18, y0 - 6)), cv2.FONT_HERSHEY_SIMPLEX, 0.52, color, 2)

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
    print("   DDZ/CDD Joker In-Hand & Fanned Hand Data Augmentation Engine   ")
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

    poker_train_img_dir = os.path.join(args.poker_dir, "train", "images")
    poker_train_lbl_dir = os.path.join(args.poker_dir, "train", "labels")
    poker_val_img_dir = os.path.join(args.poker_dir, "valid", "images")
    poker_val_lbl_dir = os.path.join(args.poker_dir, "valid", "labels")

    os.makedirs(poker_train_img_dir, exist_ok=True)
    os.makedirs(poker_train_lbl_dir, exist_ok=True)
    os.makedirs(poker_val_img_dir, exist_ok=True)
    os.makedirs(poker_val_lbl_dir, exist_ok=True)

    # 3. Collect standard card crops from poker--2 for synthetic hands
    print("\nCollecting card crops for hand synthesis...")
    std_crops = collect_standard_card_crops(poker_train_img_dir, max_crops=300)
    print(f"  [OK] Gathered {len(std_crops)} background card crops.")

    # 4. Generate Training Set
    print("\n" + "=" * 65)
    print(f"STEP 3: Generating in-hand fanned training dataset (Target: {args.target_train_count} per class)")
    print("=" * 65)

    c52_count = 0
    c53_count = 0
    train_idx = 0
    preview_images = []
    preview_annos = []

    pbar = tqdm(total=args.target_train_count * 2, desc="Generating Train Hand Samples")

    while c52_count < args.target_train_count or c53_count < args.target_train_count:
        # Balance selection
        if c52_count < c53_count:
            target_seed = random.choice(seeds_black)
        elif c53_count < c52_count:
            target_seed = random.choice(seeds_red)
        else:
            target_seed = random.choice(seeds_black if random.random() < 0.5 else seeds_red)

        # Mode distribution: 50% In-Hand CutMix, 35% Synthetic Fanned Hand, 15% Tabletop Flat
        mode_roll = random.random()
        if mode_roll < 0.50:
            aug_img, annos = generate_inhand_cutmix(poker_train_img_dir, target_seed, args.img_size)
        elif mode_roll < 0.85:
            aug_img, annos = generate_fanned_hand(target_seed, std_crops, args.img_size)
        else:
            aug_img, annos = generate_isolated_card(target_seed, poker_train_img_dir, args.img_size)

        if aug_img is None or not annos:
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
        elif args.preview_only:
            break

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
    print(f"  [OK] Train set generated: {train_idx} hand images (Class 52: {c52_count}, Class 53: {c53_count})")

    if args.preview_only:
        # Save Visual Inspection Grid immediately
        preview_path = os.path.join(os.path.dirname(__file__), "augment_preview.jpg")
        draw_preview_grid(preview_images, preview_annos, preview_path)
        print("\n" + "=" * 65)
        print("PREVIEW MODE COMPLETED: Visual inspection grid saved.")
        print(f"File: {preview_path}")
        print("=" * 65)
        return

    # 5. Generate Validation Set
    print("\n" + "=" * 65)
    print(f"STEP 4: Generating validation dataset (Target: {args.target_val_count} per class)")
    print("=" * 65)

    v52_count = 0
    v53_count = 0
    val_idx = 0
    val_pbar = tqdm(total=args.target_val_count * 2, desc="Generating Val Hand Samples")

    while v52_count < args.target_val_count or v53_count < args.target_val_count:
        if v52_count < v53_count:
            target_seed = random.choice(seeds_black)
        elif v53_count < v52_count:
            target_seed = random.choice(seeds_red)
        else:
            target_seed = random.choice(seeds_black if random.random() < 0.5 else seeds_red)

        mode_roll = random.random()
        if mode_roll < 0.50:
            aug_img, annos = generate_inhand_cutmix(poker_train_img_dir, target_seed, args.img_size)
        elif mode_roll < 0.85:
            aug_img, annos = generate_fanned_hand(target_seed, std_crops, args.img_size)
        else:
            aug_img, annos = generate_isolated_card(target_seed, poker_train_img_dir, args.img_size)

        if aug_img is None or not annos:
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
            img_out = os.path.join(poker_val_lbl_dir.replace("labels", "images"), f"{stem}.jpg")
            lbl_out = os.path.join(poker_val_lbl_dir, f"{stem}.txt")

            cv2.imwrite(img_out, aug_img, [cv2.IMWRITE_JPEG_QUALITY, 95])
            with open(lbl_out, "w", encoding="utf-8") as f:
                for cls_id, cx, cy, bw, bh in annos:
                    f.write(f"{cls_id} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")

        val_idx += 1

    val_pbar.close()
    print(f"  [OK] Validation set generated: {val_idx} hand images (Class 52: {v52_count}, Class 53: {v53_count})")

    # 6. Update data.yaml
    poker_yaml = os.path.join(args.poker_dir, "data.yaml")
    update_poker_data_yaml(poker_yaml)

    # 7. Save Visual Inspection Grid
    preview_path = os.path.join(os.path.dirname(__file__), "augment_preview.jpg")
    draw_preview_grid(preview_images, preview_annos, preview_path)

    print("\n" + "=" * 65)
    print("SUCCESS: Joker In-Hand Data Augmentation Finished!")
    print(f"Inspect '{preview_path}' to see synthesized in-hand fanned cards.")
    print("=" * 65)

if __name__ == "__main__":
    main()
