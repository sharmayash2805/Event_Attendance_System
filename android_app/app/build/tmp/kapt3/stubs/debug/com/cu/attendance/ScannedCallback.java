package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J\u001a\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007H&\u00a8\u0006\b"}, d2 = {"Lcom/cu/attendance/ScannedCallback;", "", "onResult", "", "status", "", "student", "Lcom/cu/attendance/StudentEntity;", "app_debug"})
public abstract interface ScannedCallback {
    
    public abstract void onResult(@org.jetbrains.annotations.NotNull
    java.lang.String status, @org.jetbrains.annotations.Nullable
    com.cu.attendance.StudentEntity student);
}