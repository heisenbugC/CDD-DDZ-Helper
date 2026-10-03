import os
import glob
import shutil

def main():
    jokers_dir = r"K:\IdeaWorkspace\CDD_DDZ_Helper\training\jokers"
    poker_dir = r"K:\IdeaWorkspace\CDD_DDZ_Helper\training\poker--2"

    if not os.path.exists(jokers_dir):
        print(f"Please extract the jokers dataset into {jokers_dir}")
        return

    # We shift the Joker class IDs by 52 so they don't overwrite the 0-51 playing cards
    class_id_offset = 52 

    for split in ["train", "valid", "test"]:
        joker_lbl_dir = os.path.join(jokers_dir, split, "labels")
        joker_img_dir = os.path.join(jokers_dir, split, "images")
        
        poker_lbl_dir = os.path.join(poker_dir, split, "labels")
        poker_img_dir = os.path.join(poker_dir, split, "images")
        
        if not os.path.exists(joker_lbl_dir): continue
        
        for lbl_file in glob.glob(os.path.join(joker_lbl_dir, "*.txt")):
            # 1. Read and modify the label file
            with open(lbl_file, "r") as f:
                lines = f.readlines()
            
            new_lines = []
            for line in lines:
                parts = line.strip().split()
                if not parts: continue
                
                original_class = int(parts[0])
                new_class = original_class + class_id_offset
                parts[0] = str(new_class)
                new_lines.append(" ".join(parts))
                
            # 2. Write the modified label to the poker dataset
            base_name = os.path.basename(lbl_file)
            dest_lbl = os.path.join(poker_lbl_dir, "joker_" + base_name)
            with open(dest_lbl, "w") as f:
                f.write("\n".join(new_lines) + "\n")
                
            # 3. Copy the corresponding image over
            img_name = base_name.replace(".txt", ".jpg")
            src_img = os.path.join(joker_img_dir, img_name)
            dest_img = os.path.join(poker_img_dir, "joker_" + img_name)
            
            if os.path.exists(src_img):
                shutil.copy(src_img, dest_img)

    print("Datasets successfully merged! The jokers have been added to poker--2.")

if __name__ == "__main__":
    main()
