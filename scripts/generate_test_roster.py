import pandas as pd

N = 600
uids = [f"TEST{str(i).zfill(5)}" for i in range(1, N+1)]
names = [f"Test User {str(i).zfill(3)}" for i in range(1, N+1)]
branches = ['CS', 'ECE', 'ME', 'EE', 'CE']
years = ['1st Year', '2nd Year', '3rd Year', '4th Year']

data = {
    'UID': uids,
    'Name': names,
    'Branch': [branches[i % len(branches)] for i in range(N)],
    'Year': [years[i % len(years)] for i in range(N)],
}

df = pd.DataFrame(data)
out = 'test_roster_600.xlsx'
df.to_excel(out, index=False, sheet_name='Students')
print('Wrote', out)
