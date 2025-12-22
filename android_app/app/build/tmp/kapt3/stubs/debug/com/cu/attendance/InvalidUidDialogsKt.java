package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00006\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\u001ak\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032K\u0010\u0004\u001aG\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\u0007\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\b\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b\u0006\u0012\b\b\u0007\u0012\u0004\b\b(\t\u0012\u0004\u0012\u00020\u00010\u00052\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0007\u001a\u001e\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u000e2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0007\u001a,\u0010\u000f\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0007\u001a\u000e\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012H\u0007\u001a\u000e\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00130\u0012H\u0007\u00a8\u0006\u0015"}, d2 = {"AddStudentDialog", "", "uid", "", "onSubmit", "Lkotlin/Function3;", "Lkotlin/ParameterName;", "name", "branch", "year", "onDismiss", "Lkotlin/Function0;", "AlreadyMarkedDialog", "result", "Lcom/cu/attendance/AlreadyMarkedResult;", "InvalidUidDialog", "onAddStudent", "pulseScale", "Landroidx/compose/runtime/State;", "", "shakeAnimation", "app_debug"})
public final class InvalidUidDialogsKt {
    
    @androidx.compose.runtime.Composable
    @org.jetbrains.annotations.NotNull
    public static final androidx.compose.runtime.State<java.lang.Float> shakeAnimation() {
        return null;
    }
    
    @androidx.compose.runtime.Composable
    @org.jetbrains.annotations.NotNull
    public static final androidx.compose.runtime.State<java.lang.Float> pulseScale() {
        return null;
    }
    
    @androidx.compose.runtime.Composable
    public static final void InvalidUidDialog(@org.jetbrains.annotations.NotNull
    java.lang.String uid, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onAddStudent, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss) {
    }
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable
    public static final void AddStudentDialog(@org.jetbrains.annotations.NotNull
    java.lang.String uid, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function3<? super java.lang.String, ? super java.lang.String, ? super java.lang.String, kotlin.Unit> onSubmit, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void AlreadyMarkedDialog(@org.jetbrains.annotations.NotNull
    com.cu.attendance.AlreadyMarkedResult result, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss) {
    }
}