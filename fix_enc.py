import re

def fix_file(path):
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()

    # Replaces everything matching "if (hasStrokes) <garbage>  Erase Now" else "<garbage>  Paint the watermark to erase"
    content = re.sub(
        r'if \(hasStrokes\) "[^"]+Erase Now" else "[^"]+Paint the watermark to erase"',
        r'if (hasStrokes) "✨  Erase Now" else "✨  Paint the watermark to erase"',
        content
    )

    # Replaces garbage single quotes e.g. "Saved to gallery <garbage>"
    content = re.sub(
        r'Saved to gallery [^"]+"',
        r'Saved to gallery ✨"',
        content
    )

    lines = content.split('\n')
    new_lines = []
    
    for line in lines:
        stripped = line.strip()
        # Remove garbage single-line comments full of A's entirely
        if stripped.startswith('//') and ('A' in line and line.count('A') > 10):
            continue
        # Replace garbage dividers
        if stripped.startswith('//') and ('Ã' in line or 'â' in line or '€' in line or '¦' in line):
            # Probably a divider, just change it to basic divider
            line = ' ' * (len(line) - len(line.lstrip())) + '// ----------------------------------------------------'
        
        # In case we find small arrow garbage
        if 'Ã' in line or 'â' in line:
            line = re.sub(r'Ã[^a-zA-Z0-9]*â[^a-zA-Z0-9]*¦', '✨', line)
            line = re.sub(r'Ã[^a-zA-Z0-9]*â[^a-zA-Z0-9]*\\s+', ' ', line)
            
        new_lines.append(line)
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(new_lines))

print("Fixing files...")
fix_file("app/src/main/java/com/geminieraser/app/MainActivity.kt")
fix_file("app/src/main/java/com/geminieraser/app/Components.kt")
print("Done fixing files.")
