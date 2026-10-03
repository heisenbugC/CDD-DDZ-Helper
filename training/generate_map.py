import os
import cv2
import glob
import numpy as np

labels_dir = r"K:\IdeaWorkspace\CDD_DDZ_Helper\training\poker--2\train\labels"
images_dir = r"K:\IdeaWorkspace\CDD_DDZ_Helper\training\poker--2\train\images"
yaml_path = r"K:\IdeaWorkspace\CDD_DDZ_Helper\training\poker--2\data.yaml"
out_path = r"K:\IdeaWorkspace\CDD_DDZ_Helper\class_mapping.jpg"

# Parse data.yaml to get the class names
import yaml
with open(yaml_path, 'r') as f:
    data = yaml.safe_load(f)
class_names = data['names']

# Dictionary to store one crop per class name
class_crops = {}

# Iterate through labels to find examples
for label_file in glob.glob(os.path.join(labels_dir, "*.txt")):
    with open(label_file, "r") as f:
        lines = f.readlines()
        
    for line in lines:
        parts = line.strip().split()
        if not parts: continue
        class_idx = int(parts[0])
        class_name = class_names[class_idx]
        
        if class_name not in class_crops:
            # Find the corresponding image
            base_name = os.path.basename(label_file).replace('.txt', '.jpg')
            img_path = os.path.join(images_dir, base_name)
            
            if os.path.exists(img_path):
                img = cv2.imread(img_path)
                if img is not None:
                    h, w = img.shape[:2]
                    # YOLO format: center_x, center_y, width, height (normalized)
                    cx, cy, bw, bh = map(float, parts[1:5])
                    x1 = int((cx - bw/2) * w)
                    y1 = int((cy - bh/2) * h)
                    x2 = int((cx + bw/2) * w)
                    y2 = int((cy + bh/2) * h)
                    
                    # Ensure within bounds
                    x1, y1 = max(0, x1), max(0, y1)
                    x2, y2 = min(w, x2), min(h, y2)
                    
                    crop = img[y1:y2, x1:x2]
                    if crop.size > 0:
                        # Resize to standard size for grid
                        crop = cv2.resize(crop, (100, 150))
                        class_crops[class_name] = crop
                        
    if len(class_crops) == len(class_names):
        break

# Create a grid image
cols = 8
rows = (len(class_names) + cols - 1) // cols
grid_w = cols * 100
grid_h = rows * 150
grid_img = np.ones((grid_h, grid_w, 3), dtype=np.uint8) * 255

for i, class_name in enumerate(sorted(class_crops.keys(), key=lambda x: int(x))):
    crop = class_crops[class_name]
    r = i // cols
    c = i % cols
    y = r * 150
    x = c * 100
    grid_img[y:y+150, x:x+100] = crop
    # Draw class name on top
    cv2.putText(grid_img, str(class_name), (x + 5, y + 25), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 2)

cv2.imwrite(out_path, grid_img)
print(f"Mapping saved to {out_path}")
