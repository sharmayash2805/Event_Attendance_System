package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u000e\bg\u0018\u00002\u00020\u0001J\u000e\u0010\u0002\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\n\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\u000b\u001a\u0004\u0018\u00010\u00072\u0006\u0010\f\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\u0016\u0010\u000f\u001a\u00020\u00032\u0006\u0010\u0010\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u0011J\u001c\u0010\u0012\u001a\u00020\u00032\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u00a7@\u00a2\u0006\u0002\u0010\u0014J\u001c\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\u0016\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ&\u0010\u0017\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0018\u001a\u00020\r2\u0006\u0010\u0019\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u001a\u00a8\u0006\u001b"}, d2 = {"Lcom/cu/attendance/StudentDao;", "", "deleteAll", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllStudents", "", "Lcom/cu/attendance/StudentEntity;", "getCount", "", "getPresentCount", "getStudentByUid", "uid", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insert", "student", "(Lcom/cu/attendance/StudentEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertAll", "students", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchStudents", "query", "updateStatus", "status", "timestamp", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao
public abstract interface StudentDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object insertAll(@org.jetbrains.annotations.NotNull
    java.util.List<com.cu.attendance.StudentEntity> students, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull
    com.cu.attendance.StudentEntity student, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM students")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getAllStudents(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.cu.attendance.StudentEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM students WHERE uid = :uid LIMIT 1")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getStudentByUid(@org.jetbrains.annotations.NotNull
    java.lang.String uid, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.cu.attendance.StudentEntity> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM students WHERE name LIKE \'%\' || :query || \'%\' OR uid LIKE \'%\' || :query || \'%\' ORDER BY name")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object searchStudents(@org.jetbrains.annotations.NotNull
    java.lang.String query, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.cu.attendance.StudentEntity>> $completion);
    
    @androidx.room.Query(value = "UPDATE students SET status = :status, timestamp = :timestamp WHERE uid = :uid")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object updateStatus(@org.jetbrains.annotations.NotNull
    java.lang.String uid, @org.jetbrains.annotations.NotNull
    java.lang.String status, @org.jetbrains.annotations.NotNull
    java.lang.String timestamp, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM students WHERE status = \'Present\'")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getPresentCount(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM students")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getCount(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    @androidx.room.Query(value = "DELETE FROM students")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object deleteAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}