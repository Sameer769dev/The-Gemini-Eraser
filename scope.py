import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
with open('app/src/main/java/com/geminieraser/app/MainActivity.kt', encoding='utf-8') as f:
    lines = f.readlines()

indent_level = 0
for i, line in enumerate(lines):
    delta = line.count('{') - line.count('}')
    indent_level += delta
    clean = line.replace('\n', '').replace('\r', '')
    print(f'{i+1:04} {indent_level} {clean[:60]}')
