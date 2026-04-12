import os

def fix_file(path):
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()

    # We use byte strings converted to strings to avoid powershell parsing issues
    content = content.replace(b'\xc3\x83\xc6\x92\xc3\xa2\xc5\x93\xc3\x82\xc2\xa6'.decode('utf-8'), '\u2728')
    content = content.replace(b'\xc3\x83\xc6\x92\xc3\xa2\xc2\x80\xc2\xa0\xc3\x82\xc2\xa0'.decode('utf-8'), '\u2190')
    content = content.replace(b'\xc3\x83\xc6\x92\xc3\xa2\xc2\x80\xc2\xa0\xc3\xa2\xc2\x80\xc2\x99'.decode('utf-8'), '\u2192')
    content = content.replace(b'\xc3\x83\xc6\x92\xc3\xa2\xc2\x80\xc5\xa1\xc3\xa2\xc2\x80\xc2\x94'.decode('utf-8'), '\u2014')
    
    lines = content.split('\n')
    new_lines = []
    
    for line in lines:
        if 'A\'A,AAA' in line or 'AAA,A' in line or 'A,A?A' in line or 'A?sAA' in line:
            if line.strip().startswith('//'):
                continue
        if b'\xc3\x83\xc6\x92\xc3\xa2\xc2\x80\xc2\x9d\xc3\xa2\xc2\x80\xc5\xa1\xc3\x82\xc2\xac'.decode('utf-8') in line:
            if line.strip().startswith('//'):
                continue
        new_lines.append(line)
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(new_lines))

fix_file('app/src/main/java/com/vanishly/app/MainActivity.kt')
fix_file('app/src/main/java/com/vanishly/app/Components.kt')
