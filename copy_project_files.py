import os

# Source directory (your project root)
source_dir = '.'

# Output file
output_file = 'all_project_files.txt'

# File extensions/patterns to include
patterns = [
    '.kt',      # Kotlin source files
    '.kts',     # Gradle scripts
    '.json',    # Assets like questions.json
    '.xml',     # Manifests, layouts, resources
    '.toml',    # Lib versions
    'gradlew',  # Gradle wrapper (no extension)
    'gradlew.bat'
]

# Directories to ignore (build artifacts, Git, etc.)
ignore_dirs = ['.git', '.gradle', '.idea', 'build', 'app/build', '.kotlin']

# Open the output file in write mode
with open(output_file, 'w', encoding='utf-8') as out_f:
    # Walk through the source directory
    for root, dirs, files in os.walk(source_dir):
        # Skip ignored directories
        if any(ignore in root for ignore in ignore_dirs):
            continue
        
        # Process matching files
        for file in files:
            if any(file.endswith(pattern) or file == pattern for pattern in patterns):
                src_file = os.path.join(root, file)
                rel_path = os.path.relpath(src_file, source_dir)
                
                # Write header
                out_f.write(f"----- File: {rel_path} -----\n\n")
                
                # Write content
                try:
                    with open(src_file, 'r', encoding='utf-8') as in_f:
                        content = in_f.read()
                        out_f.write(content)
                except UnicodeDecodeError:
                    out_f.write("[Binary or non-UTF-8 file - content skipped]\n")
                
                # Separator
                out_f.write("\n\n----- End of File -----\n\n")
    
print(f'\nAll relevant files combined into "{output_file}". You can now open this text file in Notepad and copy-paste its contents here (it might be large, so do it in parts if needed)!')