package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000T\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000f\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\"\u001a\u00020#2\u0006\u0010$\u001a\u00020%2\u0006\u0010&\u001a\u00020\u0013H\u0002J\u0016\u0010\'\u001a\u00020#2\f\u0010(\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u0002J\u0012\u0010)\u001a\u00020#2\b\u0010*\u001a\u0004\u0018\u00010+H\u0014J\u0018\u0010,\u001a\u00020#2\u0006\u0010-\u001a\u00020.2\u0006\u0010/\u001a\u000200H\u0002J\b\u00101\u001a\u000200H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R7\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00068B@BX\u0082\u008e\u0002\u00a2\u0006\u0012\n\u0004\b\r\u0010\u000e\u001a\u0004\b\t\u0010\n\"\u0004\b\u000b\u0010\fR\u001c\u0010\u000f\u001a\u0010\u0012\f\u0012\n \u0012*\u0004\u0018\u00010\u00110\u00110\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R+\u0010\u0014\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00138B@BX\u0082\u008e\u0002\u00a2\u0006\u0012\n\u0004\b\u0019\u0010\u000e\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R+\u0010\u001a\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00138B@BX\u0082\u008e\u0002\u00a2\u0006\u0012\n\u0004\b\u001d\u0010\u000e\u001a\u0004\b\u001b\u0010\u0016\"\u0004\b\u001c\u0010\u0018R+\u0010\u001e\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00138B@BX\u0082\u008e\u0002\u00a2\u0006\u0012\n\u0004\b!\u0010\u000e\u001a\u0004\b\u001f\u0010\u0016\"\u0004\b \u0010\u0018\u00a8\u00062"}, d2 = {"Lcom/cu/attendance/MainActivity;", "Landroidx/activity/ComponentActivity;", "()V", "database", "Lcom/cu/attendance/AppDatabase;", "<set-?>", "", "Lcom/cu/attendance/StudentImportRow;", "importedStudents", "getImportedStudents", "()Ljava/util/List;", "setImportedStudents", "(Ljava/util/List;)V", "importedStudents$delegate", "Landroidx/compose/runtime/MutableState;", "scannerLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "kotlin.jvm.PlatformType", "", "showExportScreen", "getShowExportScreen", "()Z", "setShowExportScreen", "(Z)V", "showExportScreen$delegate", "showImportScreen", "getShowImportScreen", "setShowImportScreen", "showImportScreen$delegate", "showSearchScreen", "getShowSearchScreen", "setShowSearchScreen", "showSearchScreen$delegate", "exportAttendance", "", "format", "Lcom/cu/attendance/ui/export/ExportFormat;", "presentOnly", "importToDatabase", "students", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "shareFile", "file", "Ljava/io/File;", "mimeType", "", "timeStamp", "app_debug"})
public final class MainActivity extends androidx.activity.ComponentActivity {
    @org.jetbrains.annotations.NotNull
    private final androidx.compose.runtime.MutableState showSearchScreen$delegate = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.compose.runtime.MutableState showImportScreen$delegate = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.compose.runtime.MutableState importedStudents$delegate = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.compose.runtime.MutableState showExportScreen$delegate = null;
    private com.cu.attendance.AppDatabase database;
    @org.jetbrains.annotations.NotNull
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> scannerLauncher = null;
    
    public MainActivity() {
        super();
    }
    
    private final boolean getShowSearchScreen() {
        return false;
    }
    
    private final void setShowSearchScreen(boolean p0) {
    }
    
    private final boolean getShowImportScreen() {
        return false;
    }
    
    private final void setShowImportScreen(boolean p0) {
    }
    
    private final java.util.List<com.cu.attendance.StudentImportRow> getImportedStudents() {
        return null;
    }
    
    private final void setImportedStudents(java.util.List<com.cu.attendance.StudentImportRow> p0) {
    }
    
    private final boolean getShowExportScreen() {
        return false;
    }
    
    private final void setShowExportScreen(boolean p0) {
    }
    
    @java.lang.Override
    protected void onCreate(@org.jetbrains.annotations.Nullable
    android.os.Bundle savedInstanceState) {
    }
    
    private final void importToDatabase(java.util.List<com.cu.attendance.StudentImportRow> students) {
    }
    
    private final void exportAttendance(com.cu.attendance.ui.export.ExportFormat format, boolean presentOnly) {
    }
    
    private final java.lang.String timeStamp() {
        return null;
    }
    
    private final void shareFile(java.io.File file, java.lang.String mimeType) {
    }
}