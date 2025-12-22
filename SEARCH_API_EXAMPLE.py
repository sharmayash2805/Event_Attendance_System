# Add this to your Flask app.py to support student search

@app.route('/search_student', methods=['GET'])
def search_student():
    """
    Search for students by name or UID
    Returns student data including year and department from Excel sheet
    """
    query = request.args.get('query', '').strip().lower()
    
    if len(query) < 2:
        return jsonify({"students": []}), 200
    
    try:
        # Read the Excel sheet
        df = pd.read_excel('student_data.xlsx')  # or your sheet name
        
        # Search in Name and UID columns (case-insensitive)
        mask = (
            df['Name'].str.lower().str.contains(query, na=False) |
            df['UID'].str.lower().str.contains(query, na=False)
        )
        
        results = df[mask]
        
        # Format response
        students = []
        for _, row in results.iterrows():
            students.append({
                'uid': str(row['UID']),
                'name': str(row['Name']),
                'year': str(row.get('Year', 'N/A')),  # Column name in your sheet
                'department': str(row.get('Department', 'N/A'))  # Column name in your sheet
            })
        
        return jsonify({
            'students': students
        }), 200
        
    except Exception as e:
        print(f"Search error: {str(e)}")
        return jsonify({
            'error': 'Search failed',
            'message': str(e)
        }), 500


# Expected Excel sheet columns:
# UID | Name | Year | Department | [other columns...]
# Example:
# 22BCS024 | Alex Johnson | 3rd Year | CS
# 22BCS015 | Sarah Williams | 3rd Year | CS


# Add student endpoint (example)

@app.route('/add_student', methods=['POST'])
def add_student():
    """
    Add a new student to the database/Excel sheet
    Auto-called when scanner finds invalid UID
    """
    try:
        data = request.json
        uid = data.get('uid')
        name = data.get('name')
        branch = data.get('branch')  # Department/Branch
        year = data.get('year')
        
        if not all([uid, name, branch, year]):
            return jsonify({
                'status': 'error',
                'message': 'Missing required fields'
            }), 400
        
        # Read existing sheet
        df = pd.read_excel('student_data.xlsx')
        
        # Check if UID already exists
        if uid in df['UID'].values:
            return jsonify({
                'status': 'error',
                'message': 'UID already exists'
            }), 409
        
        # Add new row
        new_row = pd.DataFrame({
            'UID': [uid],
            'Name': [name],
            'Year': [year],
            'Department': [branch],
            # Add other columns with default values if needed
            # 'Present': ['No'],
            # 'Timestamp': ['']
        })
        
        df = pd.concat([df, new_row], ignore_index=True)
        
        # Save back to Excel
        df.to_excel('student_data.xlsx', index=False)
        
        return jsonify({
            'status': 'success',
            'message': f'Student {name} added successfully',
            'uid': uid
        }), 200
        
    except Exception as e:
        print(f"Add student error: {str(e)}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500


# Usage flow example:
# 1) Scanner calls /mark_attendance and receives 404 for unknown UID.
# 2) App shows "Invalid UID" dialog and offers Add Student.
# 3) User submits name/branch/year via POST /add_student.
# 4) Backend writes to Excel then app re-calls /mark_attendance.


# Handle already marked (409 response) in /mark_attendance:

@app.route('/mark_attendance', methods=['POST'])
def mark_attendance():
    """
    Mark student attendance
    Returns 409 if already marked
    """
    try:
        data = request.json
        uid = data.get('uid')
        
        if not uid:
            return jsonify({'error': 'UID required'}), 400
        
        # Read Excel sheet
        df = pd.read_excel('student_data.xlsx')
        
        # Find student
        student = df[df['UID'] == uid]
        
        if student.empty:
            return jsonify({'error': 'Student not found'}), 404
        
        # Check if already marked (assuming you have a 'Status' or 'Present' column)
        # Adjust column names based on your sheet structure
        if 'Status' in df.columns:
            current_status = student.iloc[0]['Status']
            if current_status == 'Present':
                # Already marked - return 409
                return jsonify({
                    'status': 'already_marked',
                    'uid': uid,
                    'name': student.iloc[0]['Name'],
                    'time': student.iloc[0].get('Timestamp', 'Earlier today'),
                    'message': f"{student.iloc[0]['Name']} is already marked present"
                }), 409
        
        # Mark as present
        df.loc[df['UID'] == uid, 'Status'] = 'Present'
        df.loc[df['UID'] == uid, 'Timestamp'] = datetime.now().strftime('%d-%m-%Y %H:%M:%S')
        
        # Save
        df.to_excel('student_data.xlsx', index=False)
        
        return jsonify({
            'status': 'success',
            'uid': uid,
            'name': student.iloc[0]['Name'],
            'time': datetime.now().strftime('%d %b %Y · %H:%M'),
            'message': f"Attendance marked for {student.iloc[0]['Name']}"
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# Response codes summary
# 200 Success - attendance marked
# 404 Invalid UID - student not found → show Add Student dialog
# 409 Already marked → show warning dialog


