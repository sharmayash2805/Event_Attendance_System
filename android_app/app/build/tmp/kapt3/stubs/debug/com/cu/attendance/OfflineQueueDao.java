package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0000\bg\u0018\u00002\u00020\u0001J\u000e\u0010\u0002\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u0005\u001a\u00020\u00032\u0006\u0010\u0006\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\bJ\u0016\u0010\t\u001a\u00020\u00072\u0006\u0010\n\u001a\u00020\u000bH\u00a7@\u00a2\u0006\u0002\u0010\fJ\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000b0\u000eH\u00a7@\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u000f"}, d2 = {"Lcom/cu/attendance/OfflineQueueDao;", "", "clear", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteById", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "enqueue", "item", "Lcom/cu/attendance/OfflineQueueEntity;", "(Lcom/cu/attendance/OfflineQueueEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAll", "", "app_debug"})
@androidx.room.Dao
public abstract interface OfflineQueueDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object enqueue(@org.jetbrains.annotations.NotNull
    com.cu.attendance.OfflineQueueEntity item, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM offline_queue ORDER BY createdAt ASC")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.cu.attendance.OfflineQueueEntity>> $completion);
    
    @androidx.room.Query(value = "DELETE FROM offline_queue WHERE id = :id")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object deleteById(long id, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM offline_queue")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object clear(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}