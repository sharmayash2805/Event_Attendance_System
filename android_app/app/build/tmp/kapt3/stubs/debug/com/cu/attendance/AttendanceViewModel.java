package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004JP\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u00072\u0006\u0010\u001f\u001a\u00020\u00072\u0006\u0010 \u001a\u00020\u00072\u0006\u0010!\u001a\u00020\u00072\u0012\u0010\"\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u001d0#2\u0014\b\u0002\u0010$\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u001d0#J\u0006\u0010%\u001a\u00020\u001dJ\u0006\u0010&\u001a\u00020\u001dJ\u0014\u0010\'\u001a\b\u0012\u0004\u0012\u00020\r0\fH\u0086@\u00a2\u0006\u0002\u0010(J\b\u0010)\u001a\u00020\u0007H\u0002J\b\u0010*\u001a\u00020\u001dH\u0002JD\u0010+\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u00072\u0012\u0010\"\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u001d0#2\u0012\u0010,\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u001d0#2\f\u0010-\u001a\b\u0012\u0004\u0012\u00020\u001d0.J\u000e\u0010/\u001a\u00020\u001d2\u0006\u00100\u001a\u00020\u0007R\u0016\u0010\u0005\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u0012\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0017\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\t0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0015R\u0017\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\t0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0015R\u001d\u0010\u0018\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0015R\u0017\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u0015\u00a8\u00061"}, d2 = {"Lcom/cu/attendance/AttendanceViewModel;", "Landroidx/lifecycle/AndroidViewModel;", "application", "Landroid/app/Application;", "(Landroid/app/Application;)V", "_error", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "_isMarking", "", "_isSearching", "_searchResults", "", "Lcom/cu/attendance/StudentEntity;", "_stats", "Lcom/cu/attendance/AttendanceStats;", "database", "Lcom/cu/attendance/AppDatabase;", "error", "Lkotlinx/coroutines/flow/StateFlow;", "getError", "()Lkotlinx/coroutines/flow/StateFlow;", "isMarking", "isSearching", "searchResults", "getSearchResults", "stats", "getStats", "addStudentAndMarkPresent", "", "uid", "name", "branch", "year", "onSuccess", "Lkotlin/Function1;", "onError", "clearError", "clearSearchResults", "getAllStudentsForExport", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getCurrentTimestamp", "loadStats", "markStudentPresent", "onAlreadyMarked", "onInvalid", "Lkotlin/Function0;", "searchStudents", "query", "app_debug"})
public final class AttendanceViewModel extends androidx.lifecycle.AndroidViewModel {
    @org.jetbrains.annotations.NotNull
    private final com.cu.attendance.AppDatabase database = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.cu.attendance.AttendanceStats> _stats = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.cu.attendance.AttendanceStats> stats = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.cu.attendance.StudentEntity>> _searchResults = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.cu.attendance.StudentEntity>> searchResults = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _isSearching = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isSearching = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _isMarking = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isMarking = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.String> _error = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.lang.String> error = null;
    
    public AttendanceViewModel(@org.jetbrains.annotations.NotNull
    android.app.Application application) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.cu.attendance.AttendanceStats> getStats() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.cu.attendance.StudentEntity>> getSearchResults() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isSearching() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isMarking() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.lang.String> getError() {
        return null;
    }
    
    private final void loadStats() {
    }
    
    public final void searchStudents(@org.jetbrains.annotations.NotNull
    java.lang.String query) {
    }
    
    public final void clearSearchResults() {
    }
    
    public final void markStudentPresent(@org.jetbrains.annotations.NotNull
    java.lang.String uid, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.cu.attendance.StudentEntity, kotlin.Unit> onSuccess, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.cu.attendance.StudentEntity, kotlin.Unit> onAlreadyMarked, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onInvalid) {
    }
    
    public final void addStudentAndMarkPresent(@org.jetbrains.annotations.NotNull
    java.lang.String uid, @org.jetbrains.annotations.NotNull
    java.lang.String name, @org.jetbrains.annotations.NotNull
    java.lang.String branch, @org.jetbrains.annotations.NotNull
    java.lang.String year, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.cu.attendance.StudentEntity, kotlin.Unit> onSuccess, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onError) {
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object getAllStudentsForExport(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.cu.attendance.StudentEntity>> $completion) {
        return null;
    }
    
    private final java.lang.String getCurrentTimestamp() {
        return null;
    }
    
    public final void clearError() {
    }
}