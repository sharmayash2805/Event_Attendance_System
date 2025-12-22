# Excel Import Format

## Expected Excel Sheet Structure

Your Excel file (.xlsx) should have the following columns in order:

| Column A | Column B | Column C | Column D |
|----------|----------|----------|----------|
| UID      | Name     | Branch   | Year     |

### Example Data:

| UID      | Name           | Branch | Year     |
|----------|----------------|--------|----------|
| 22BCS024 | Alex Johnson   | CS     | 3rd Year |
| 22BCS015 | Sarah Williams | CS     | 3rd Year |
| 22BCE032 | Emma Davis     | ECE    | 2nd Year |
| 22BME018 | Michael Brown  | ME     | 4th Year |
| 22BCS007 | Priya Sharma   | CS     | 3rd Year |

### Validation Rules:

✅ **Valid Row**:
- UID must not be empty
- Name must not be empty
- Branch and Year are optional but recommended

❌ **Invalid Row** (will be highlighted in red):
- Missing UID
- Missing Name
- Empty cells in UID or Name columns

### Notes:

1. **Header Row**: The first row is assumed to be headers and will be skipped
2. **File Format**: Only `.xlsx` format is supported (Excel 2007+)
3. **Duplicates**: If a UID already exists in the database, it will be replaced with new data
4. **Large Files**: The app can handle thousands of students efficiently

### Import Process:

1. Click "IMPORT LIST" button on home screen
2. Select your Excel file from device storage
3. Review the preview screen:
   - Total count
   - Valid rows (green checkmark)
   - Invalid rows (red error icon)
4. Click "CONFIRM & IMPORT" to add students to local database
5. Students are now available for search and attendance marking

### Offline Storage:

- All imported data is stored locally in SQLite database (Room)
- No internet connection required
- Data persists across app restarts
