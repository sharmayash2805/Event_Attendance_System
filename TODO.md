# TODO: Fix Offline-First Android Attendance App

## Critical Fixes (Offline-First Compliance)
- [ ] Update StudentDao.kt: Add queries for search by name/UID, update status/timestamp, get present count, etc.
- [x] Refactor SearchScreen.kt: Replace Retrofit API calls with ViewModel operations for search, mark present, add student.
- [x] Remove network check from ScannerScreen.kt: Make online/offline indicator static or remove if not needed.
- [ ] Update HomeScreen.kt: Make stats dynamic by fetching from Room DB.

## State Management & Architecture
- [ ] Create AttendanceViewModel.kt: Handle all DB operations, state for stats, search results, etc.
- [ ] Update MainActivity.kt: Integrate ViewModel, pass it to screens.
- [ ] Ensure no blocking calls on main thread: All DB ops in coroutines with IO dispatcher.

## Safety & UX Improvements
- [ ] Add error handling: Try-catch for DB operations, show user-friendly messages.
- [ ] Add confirmation dialogs: For import, export, delete all (if needed).
- [ ] Add loading states: For search, import, mark present.
- [ ] Add input validation: For add student form.
- [ ] Add comments: Document complex logic, TODOs for future improvements.

## Performance Optimizations
- [ ] Add Room indices: On uid, name for faster search.
- [ ] Optimize queries: Use Flow for reactive updates where possible.
- [ ] Better coroutine usage: Use viewModelScope, proper cancellation.

## Export Feature Implementation
- [ ] Implement export in MainActivity.kt: CSV/Excel export using Room data.
- [ ] Add file writing safety: Use proper file I/O, handle permissions.

## Testing & Final Checks
- [ ] Verify all flows: Import, search, mark present, add student, export.
- [ ] Ensure no network dependencies during event.
- [ ] Check recomposition: No unnecessary recompositions in Compose screens.
- [ ] Add logging: For debugging, but not in production.
