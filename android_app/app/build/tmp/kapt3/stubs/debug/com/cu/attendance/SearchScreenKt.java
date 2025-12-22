package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00006\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0000\u001a\u001e\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00010\u0005H\u0007\u001a\u001e\u0010\u0006\u001a\u00020\u00012\u0006\u0010\u0007\u001a\u00020\b2\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00010\u0005H\u0007\u001a\u0016\u0010\n\u001a\u00020\u00012\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00010\u0005H\u0007\u001a$\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u000e2\u0012\u0010\u000f\u001a\u000e\u0012\u0004\u0012\u00020\u000e\u0012\u0004\u0012\u00020\u00010\u0010H\u0007\u001a\u0006\u0010\u0011\u001a\u00020\u0012\u001a\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0014\u00a8\u0006\u0015"}, d2 = {"AttendanceSuccessDialog", "", "result", "Lcom/cu/attendance/AttendanceResult;", "onDismiss", "Lkotlin/Function0;", "MarkPresentButton", "enabled", "", "onClick", "SearchScreen", "onBack", "StudentCard", "student", "Lcom/cu/attendance/Student;", "onMarkPresent", "Lkotlin/Function1;", "getCurrentTime", "", "getSampleStudents", "", "app_debug"})
public final class SearchScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable
    public static final void SearchScreen(@org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onBack) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void StudentCard(@org.jetbrains.annotations.NotNull
    com.cu.attendance.Student student, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.cu.attendance.Student, kotlin.Unit> onMarkPresent) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void MarkPresentButton(boolean enabled, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void AttendanceSuccessDialog(@org.jetbrains.annotations.NotNull
    com.cu.attendance.AttendanceResult result, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss) {
    }
    
    @org.jetbrains.annotations.NotNull
    public static final java.lang.String getCurrentTime() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public static final java.util.List<com.cu.attendance.Student> getSampleStudents() {
        return null;
    }
}