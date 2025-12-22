package com.cu.attendance;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 -2\u00020\u0001:\u0001-B\u0005\u00a2\u0006\u0002\u0010\u0002J(\u0010\u0017\u001a\u00020\u00132\u0006\u0010\u0018\u001a\u00020\u00122\u0006\u0010\u0019\u001a\u00020\u00122\u0006\u0010\u001a\u001a\u00020\u00122\u0006\u0010\u001b\u001a\u00020\u0012H\u0002J \u0010\u001c\u001a\u00020\u00132\u0006\u0010\u001d\u001a\u00020\u000e2\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020!H\u0002J\u0012\u0010\"\u001a\u00020\u00132\b\u0010#\u001a\u0004\u0018\u00010$H\u0014J\b\u0010%\u001a\u00020\u0013H\u0014J\u0010\u0010&\u001a\u00020\u00132\u0006\u0010\'\u001a\u00020(H\u0003J\u0018\u0010)\u001a\u00020\u00132\u0006\u0010*\u001a\u00020\u00122\u0006\u0010+\u001a\u00020\u000bH\u0002J\u0018\u0010,\u001a\u00020\u00132\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020!H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\rX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0010\u001a\u0010\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u0013\u0018\u00010\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0014\u001a\u0010\u0012\f\u0012\n \u0016*\u0004\u0018\u00010\u00120\u00120\u0015X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006."}, d2 = {"Lcom/cu/attendance/BarcodeScannerActivity;", "Landroidx/activity/ComponentActivity;", "()V", "barcodeScanner", "Lcom/google/mlkit/vision/barcode/BarcodeScanner;", "camera", "Landroidx/camera/core/Camera;", "cameraExecutor", "Ljava/util/concurrent/ExecutorService;", "cameraPermissionGranted", "Landroidx/compose/runtime/MutableState;", "", "cameraProviderFuture", "Lcom/google/common/util/concurrent/ListenableFuture;", "Landroidx/camera/lifecycle/ProcessCameraProvider;", "isScanning", "onScanListener", "Lkotlin/Function1;", "", "", "permissionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "kotlin.jvm.PlatformType", "addStudentAndMarkAttendance", "uid", "name", "branch", "year", "bindCameraUseCases", "cameraProvider", "previewView", "Landroidx/camera/view/PreviewView;", "lifecycleOwner", "Landroidx/lifecycle/LifecycleOwner;", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "processImageProxy", "imageProxy", "Landroidx/camera/core/ImageProxy;", "showResult", "message", "success", "startCamera", "Companion", "app_debug"})
public final class BarcodeScannerActivity extends androidx.activity.ComponentActivity {
    private com.google.common.util.concurrent.ListenableFuture<androidx.camera.lifecycle.ProcessCameraProvider> cameraProviderFuture;
    private java.util.concurrent.ExecutorService cameraExecutor;
    @org.jetbrains.annotations.NotNull
    private com.google.mlkit.vision.barcode.BarcodeScanner barcodeScanner;
    @org.jetbrains.annotations.Nullable
    private androidx.camera.core.Camera camera;
    private boolean isScanning = true;
    @org.jetbrains.annotations.Nullable
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onScanListener;
    @org.jetbrains.annotations.NotNull
    private final androidx.compose.runtime.MutableState<java.lang.Boolean> cameraPermissionGranted = null;
    @org.jetbrains.annotations.NotNull
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> permissionLauncher = null;
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String TAG = "BarcodeScannerActivity";
    @org.jetbrains.annotations.NotNull
    public static final com.cu.attendance.BarcodeScannerActivity.Companion Companion = null;
    
    public BarcodeScannerActivity() {
        super();
    }
    
    @java.lang.Override
    protected void onCreate(@org.jetbrains.annotations.Nullable
    android.os.Bundle savedInstanceState) {
    }
    
    private final void startCamera(androidx.camera.view.PreviewView previewView, androidx.lifecycle.LifecycleOwner lifecycleOwner) {
    }
    
    private final void bindCameraUseCases(androidx.camera.lifecycle.ProcessCameraProvider cameraProvider, androidx.camera.view.PreviewView previewView, androidx.lifecycle.LifecycleOwner lifecycleOwner) {
    }
    
    @androidx.annotation.OptIn(markerClass = {androidx.camera.core.ExperimentalGetImage.class})
    private final void processImageProxy(androidx.camera.core.ImageProxy imageProxy) {
    }
    
    private final void addStudentAndMarkAttendance(java.lang.String uid, java.lang.String name, java.lang.String branch, java.lang.String year) {
    }
    
    private final void showResult(java.lang.String message, boolean success) {
    }
    
    @java.lang.Override
    protected void onDestroy() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/cu/attendance/BarcodeScannerActivity$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}