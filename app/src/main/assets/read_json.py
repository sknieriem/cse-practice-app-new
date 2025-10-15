import json
from collections import Counter
import os

# Load the JSON file
with open('questions.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Flatten the list if there are multiple inner lists
questions = []
for sublist in data:
    questions.extend(sublist)

# Deduplicate based on 'text', keeping the first occurrence
unique_questions = {}
for q in questions:
    text = q.get('text', '')
    if text and text not in unique_questions:  # Ensure text is not empty
        unique_questions[text] = q

# Clean and format questions
cleaned_questions = []
issues = []  # To track questions with missing fields
required_fields = ['text', 'optionA', 'optionB', 'optionC', 'optionD', 'correctAnswer', 'category', 'explanation']
for q in unique_questions.values():
    cleaned_q = {}
    missing_fields = []
    for field in required_fields:
        if field not in q or q[field] is None or q[field] == '':
            if field == 'category':
                cleaned_q[field] = 'Unknown'
            elif field == 'explanation':
                cleaned_q[field] = 'No explanation provided'
            else:
                cleaned_q[field] = ''  # Default for other fields
            missing_fields.append(field)
        else:
            cleaned_q[field] = q[field]
    cleaned_questions.append(cleaned_q)
    if missing_fields:
        issues.append({'text': cleaned_q['text'], 'missing_fields': missing_fields})

# Save cleaned questions to questions_cleaned.json
with open('questions_cleaned.json', 'w', encoding='utf-8') as f:
    json.dump(cleaned_questions, f, indent=4, ensure_ascii=False)

# Count questions per category
category_counts = Counter(q['category'] for q in cleaned_questions)

# Print category counts
print("Questions per category after deduplication:")
for category, count in category_counts.items():
    print(f"{category}: {count}")

# Print issues for review
if issues:
    print("\nQuestions with missing or empty fields:")
    for issue in issues:
        print(f"Question: {issue['text'][:50]}... | Missing/Empty fields: {issue['missing_fields']}")

# Create separate files for each category
output_dir = 'category_files'
os.makedirs(output_dir, exist_ok=True)
for category in category_counts:
    # Filter questions for this category
    category_questions = [q for q in cleaned_questions if q['category'] == category]
    # Create a safe filename by replacing spaces and special characters
    safe_category = category.replace(' ', '_').replace('/', '_').replace('\\', '_')
    category_filename = os.path.join(output_dir, f"{safe_category}.json")
    with open(category_filename, 'w', encoding='utf-8') as f:
        json.dump(category_questions, f, indent=4, ensure_ascii=False)
    print(f"Saved {len(category_questions)} questions to {category_filename}")