import os
import shutil

# Directories to process
TARGET_DIRS = [
    "app",
    "backend",
    "legal",
    "website",
    "fastlane",  # if any
    ".github",   # if any
]

# Root files
ROOT_FILES = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "README.md"
]

EXTENSIONS = {".kt", ".xml", ".html", ".md", ".py", ".txt", ".json", ".kts", ".gradle"}

REPLACEMENTS = [
    ("Vanishly", "Vanishly"),
    ("vanishly", "vanishly"),
    ("Vanishly", "Vanishly"),
    ("vanishly", "vanishly"),
    ("vanishly", "vanishly"),
    ("Vanishly", "Vanishly"),
    ("vanishly", "vanishly"),
    ("Vanishly", "Vanishly") # a bit broad but we should catch any solo "Vanishly" too, though might be safer to be specific.
]

# But we only want to replace solo 'Vanishly' if we're sure. Let's look out for the case:
def replace_content(content):
    c = content
    # Order matters: replace longer strings first
    c = c.replace("Vanishly", "Vanishly")
    c = c.replace("vanishly", "vanishly")
    c = c.replace("Vanishly", "Vanishly")
    c = c.replace("vanishly", "vanishly")
    c = c.replace("vanishly", "vanishly")
    c = c.replace("Vanishly", "Vanishly")
    c = c.replace("vanishly", "vanishly")
    
    # Let's also check for specific leftover 'Vanishly' strings that we might want to replace.
    # We will just replace all "Vanishly" with "Vanishly" except where it might break things.
    # Actually, replacing all "Vanishly" with "Vanishly" might be dangerous (e.g. if using a model named vanishly somewhere).
    # But this app doesn't use the Vanishly model anymore (it uses FSRCNN and OpenCV inpainting).
    c = c.replace("Vanishly", "Vanishly")
    c = c.replace("vanishly ", "vanishly ")
    return c

def process_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        # Might be binary or unreadable
        return
        
    new_content = replace_content(content)
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated: {filepath}")

def walk_and_replace():
    for root, dirs, files in os.walk("."):
        # skip .git
        if ".git" in root or "build" in root or ".gradle" in root or "node_modules" in root or "__pycache__" in root:
            continue
            
        for name in files:
            ext = os.path.splitext(name)[1]
            if ext in EXTENSIONS or name in ROOT_FILES:
                process_file(os.path.join(root, name))

def move_packages():
    # Move app/src/main/java/com/vanishly/app -> app/src/main/java/com/vanishly/app
    
    base_dirs = [
        "app/src/main/java/com",
        "app/src/androidTest/java/com",
        "app/src/test/java/com"
    ]
    
    for base in base_dirs:
        old_dir = os.path.join(base, "vanishly")
        new_dir = os.path.join(base, "vanishly")
        
        if os.path.exists(old_dir):
            if not os.path.exists(new_dir):
                shutil.copytree(old_dir, new_dir)
                shutil.rmtree(old_dir)
                print(f"Moved directory {old_dir} to {new_dir}")
            else:
                print(f"Target directory {new_dir} already exists.")

if __name__ == "__main__":
    walk_and_replace()
    move_packages()
