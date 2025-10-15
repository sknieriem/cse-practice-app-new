import json
import os
from collections import Counter

# Directory containing the JSON files
directory = r"C:\Users\steve\AndroidStudioProjects\CSEPracticeApp\app\src\main\assets\category_files"

# Initialize list to store all questions
all_questions = []

# Read all JSON files in the directory
for filename in os.listdir(directory):
    if filename.endswith('.json'):
        file_path = os.path.join(directory, filename)
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                questions = json.load(f)
                # Ensure questions is a list
                if isinstance(questions, list):
                    all_questions.extend(questions)
                else:
                    print(f"Warning: {filename} does not contain a list of questions.")
        except Exception as e:
            print(f"Error reading {filename}: {e}")

# Deduplicate based on 'text', keeping the first occurrence
unique_questions = {}
for q in all_questions:
    text = q.get('text', '')
    if text and text not in unique_questions:  # Ensure text is not empty
        unique_questions[text] = q

# Convert to list for saving
deduplicated_questions = list(unique_questions.values())

# Save deduplicated questions to a new file
output_file = os.path.join(directory, 'combined_deduplicated_questions.json')
with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(deduplicated_questions, f, indent=4, ensure_ascii=False)

# Count total unique questions
total_questions = len(deduplicated_questions)

# Count questions per category
category_counts = Counter(q.get('category', 'Unknown') for q in deduplicated_questions)

# Print results
print(f"Total unique questions across all files: {total_questions}")
print("\nCategory breakdown:")
for category, count in category_counts.items():
    print(f"{category}: {count}")

# Check for questions with missing or invalid fields
issues = []
required_fields = ['text', 'optionA', 'optionB', 'optionC', 'optionD', 'correctAnswer', 'category', 'explanation']
for q in deduplicated_questions:
    missing_fields = [field for field in required_fields if field not in q or q[field] is None or q[field] == '']
    if missing_fields:
        issues.append({'text': q.get('text', 'No text')[:50], 'missing_fields': missing_fields})

# Report any issues
if issues:
    print("\nQuestions with missing or empty fields:")
    for issue in issues:
        print(f"Question: {issue['text']}... | Missing/Empty fields: {issue['missing_fields']}")
else:
    print("\nNo questions with missing or empty fields found.")