package com.huang.homan.camera2.View.Activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.Toast;

import com.huang.homan.camera2.R;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.nio.ByteBuffer;
import java.util.Arrays;

import io.reactivex.disposables.Disposable;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class CameraActivity extends AppCompatActivity {

    /* Log tag and shortcut */
    final static String TAG = "LOG Camera2";
    public static void ltag(String message) { Log.i(TAG, message); }

    /* Toast shortcut */
    public static void msg(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    // RxJava
    public RxPermissions rxPermissions;
    private Disposable disposable;

    // Camera setting
    public static final int REQUEST_CAMERA_CODE = 100;
    public static final String PACKAGE = "package:";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    // Vertical Screen
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // Variables
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private CameraManager mCameraManager;
    private Handler childHandler, mainHandler;
    private String mCameraID; // 0: rear camera; 1: front camera
    private ImageView iv_show; // Image conversion
    private ImageView captureIV; // Capture Button
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;

    /**
     * Initialize Camera
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) { // Open camera
            mCameraDevice = camera;
            // Preview
            takePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) { // Turn off camera
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            msg(getBaseContext(), "Camera hardware failure.");
        }
    };

    /**
     * Initial Camera2
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initCamera2() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
        mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT; // Read camera
        mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            // Process temporary photo data
            @Override
            public void onImageAvailable(ImageReader reader) {
                //mCameraDevice.close();
                // Get photo data
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    iv_show.setImageBitmap(bitmap);
                }
            }
        }, mainHandler);

        // get manager
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                // Get WRITE_EXTERNAL_STORAGE
                rxPermissions.ensureEach(WRITE_EXTERNAL_STORAGE);
            } else {
                // Open camera
                mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //region implements Preview
    /**
     * Preview Variables
     */
    private CaptureRequest.Builder previewRequestBuilder;
    private CameraCaptureSession.StateCallback previewStateCallback =
        new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                if (null == mCameraDevice) return;
                // Begin to preview
                mCameraCaptureSession = cameraCaptureSession;
                try {
                    // Camera2 functions
                    // Turn on Auto Focus
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // Turn on Flash
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    // Show up
                    CaptureRequest previewRequest = previewRequestBuilder.build();
                    mCameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                msg(getBaseContext(), "Capture Failure.");
            }
    };

    /**
     * Begin to preview
     */
    private void takePreview() {
        try {
            // CaptureRequest.Builder
            previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // surface of SurfaceView will be the object of CaptureRequest.Builder
            previewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            // Create CameraCaptureSession to take care of preview and photo shooting.
            mCameraDevice.createCaptureSession(
                    Arrays.asList(mSurfaceHolder.getSurface(),
                                  mImageReader.getSurface()),
                                  previewStateCallback,
                                  childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    //endregion implements Preview

    //region implements Permission Requests
    /*
    static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 201; // any code you want.
    public void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                ltag("Permission is granted");
            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_ID_MULTIPLE_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        ltag("Permission: " + permissions.toString());

        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // All good!
                    msg(this, "Permissions granted!");
                    ltag("Permissions granted!");
                } else {
                    msg(this, "You don't have the permission!");
                    ltag("You don't have the permission!");
                }

                break;
        }
    }
    */
//endregion implements Permission Requests


    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        //requestPermissions();

        // Permissions
        rxPermissions = new RxPermissions(this);
        rxPermissions.setLogging(true);
        rxPermissions
                .request(CAMERA,
                        READ_EXTERNAL_STORAGE,
                        WRITE_EXTERNAL_STORAGE)
                .subscribe(granted -> {
                    ltag("Permission: " + granted.toString());
                    if (granted) { // Always true pre-M
                        msg(this, "Permissions granted!");
                        ltag("Permissions granted!");
                    } else {
                        // Oops permission denied
                        msg(this, "You don't have the permission!");
                        ltag("You don't have the permission!");
                    }
                });

        // Image conversion
        iv_show = findViewById(R.id.iv_show_camera2);

        // SurfaceView
        mSurfaceView = findViewById(R.id.mySV);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);
        // mSurfaceView callback: get data
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                // Initial Camera2
                initCamera2();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) { // SurfaceView removal
                // Recycle Camera
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        });
    }


}
