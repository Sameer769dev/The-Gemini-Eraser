import re

def fix_file(path):
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()

    # 1. Fix errorMessage
    content = re.sub(
        r'"[^"]+\"',
        r'"??  "',
        content
    )

    # 2. Fix Drag to compare
    content = re.sub(
        r'"[^"]*Drag to compare[^"]*"',
        r'"? Drag to compare ?"',
        content
    )

    # 3. Add modifier to ImageArea signature
    content = re.sub(
        r'(fun ImageArea\([\s\S]*?)(\) \{)',
        r'\1    modifier:        Modifier = Modifier,\n\2',
        content,
        count=1
    )

    # 4. Use modifier in ImageArea Box
    content = re.sub(
        r'Box\(Modifier\.fillMaxSize\(\)\)',
        r'Box(modifier.fillMaxSize())',
        content,
        count=1
    )

    # 5. Pass padding explicitly to ImageArea where used
    content = re.sub(
        r'ImageArea\(\s*sourceBitmap',
        r'ImageArea(\n                    modifier         = Modifier.padding(top = 90.dp, bottom = 180.dp),\n                    sourceBitmap',
        content,
        count=1
    )

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

fix_file('app/src/main/java/com/vanishly/app/MainActivity.kt')
