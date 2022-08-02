package in.testright.watertag;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {
    private static final SparseIntArray ORIENTATIONS;

    static {
        SparseIntArray sparseIntArray = new SparseIntArray();
        ORIENTATIONS = sparseIntArray;
        sparseIntArray.append(0, 90);
        sparseIntArray.append(1, 0);
        sparseIntArray.append(2, 270);
        sparseIntArray.append(3, 180);
    }

    /* access modifiers changed from: private */
    public CameraCaptureSession cameraCaptureSessions;
    /* access modifiers changed from: private */
    public CameraDevice cameraDevice;
    /* access modifiers changed from: private */
    public CaptureRequest.Builder captureRequestBuilder;
    /* access modifiers changed from: private */
    public Uri fileUri;
    /* access modifiers changed from: private */
    public Handler mBackgroundHandler;
    CameraCaptureSession.CaptureCallback captureCallback;
    SharedPreferences.Editor editor;
    EditText exp;
    EditText foc;
    EditText iso;
    SharedPreferences prefs;
    private Size imageDimension;
    private HandlerThread mBackgroundThread;
    private AutoFitSurfaceView textureView;
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        public void onError(CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            Log.e("TEST", "Error with code: " + i);
        }
    };
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            CameraActivity.this.openCamera();
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    /* access modifiers changed from: protected */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    200); }
        SharedPreferences sharedPreferences = getSharedPreferences("Camera Settings", 0);
        prefs = sharedPreferences;
        editor = sharedPreferences.edit();
        textureView = findViewById(R.id.textureview_camera);
        if (textureView != null) {
            textureView.setSurfaceTextureListener(this.textureListener);
            buttons();
            texts();
            findViewById(R.id.auto_values).setOnClickListener(view -> auto());
            findViewById(R.id.button_back).setOnClickListener(view -> onBackPressed());
            findViewById(R.id.button_capture).setOnClickListener(view -> takePicture());
            return;
        }
        throw new AssertionError();
    }

    public void takePicture() {
        if (cameraDevice != null) {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            Bundle extras = getIntent().getExtras();
            final ContentResolver resolver = getContentResolver();
            if (extras != null) {
                this.fileUri = extras.getParcelable("Uri");
            }
            try {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
                Size[] jpegSizes = null;
                if (characteristics != null) {
                    jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                }
                if(jpegSizes==null) {
                    jpegSizes = new Size[] {new Size(1280, 720)};
                }
                final ImageReader reader = ImageReader.newInstance((jpegSizes[0].getWidth() * 720) / jpegSizes[0].getHeight(), 720, ImageFormat.JPEG, 1);
                List<Surface> outputSurface = new ArrayList<>(2);
                outputSurface.add(reader.getSurface());
                outputSurface.add(new Surface(textureView.getSurfaceTexture()));
                captureRequestBuilder.addTarget(reader.getSurface());
                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(0));
                reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    public void onImageAvailable(ImageReader imageReader) {
                        Image image;
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        process(bytes);
                        image.close();
                    }
                    private Bitmap JPEGtoARGB8888(Bitmap input ){
                        Bitmap output = null;
                        int size = input.getWidth() * input.getHeight();
                        int[] pixels = new int[size];
                        input.getPixels(pixels,0,input.getWidth(),0,0,input.getWidth(),input.getHeight());
                        output = Bitmap.createBitmap(input.getWidth(),input.getHeight(), Bitmap.Config.ARGB_8888);
                        output.setPixels(pixels, 0, output.getWidth(), 0, 0, output.getWidth(), output.getHeight());
                        return output; // ARGB_8888 formated bitmap
                    }

                    // Get image Mat from Bitmap
                    private Mat BitmapToMat(Bitmap bitmap){
                        Bitmap bitmapARGB8888 = JPEGtoARGB8888(bitmap);
                        Mat imageMat = new Mat();
                        Utils.bitmapToMat(bitmapARGB8888, imageMat);
                        return imageMat;
                    }

                    private void process(byte[] bytes) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Mat mRGBAT = BitmapToMat(bitmap);
                        Mat blur = mRGBAT.clone();
                        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size((2*2) + 1, (2*2)+1));
                        Imgproc.dilate(blur, blur, kernel);
                        Imgproc.erode(blur, blur, kernel);
                        Mat mask = new Mat(mRGBAT.rows(), mRGBAT.cols(), mRGBAT.type());
                        Core.inRange(blur, new Scalar(0,0,0,0), new Scalar(150,140,140, 255), mask);
                        List<MatOfPoint> contours = new ArrayList<>();
                        Mat hierarchey = new Mat();
                        Imgproc.findContours(mask, contours, hierarchey, Imgproc.RETR_LIST,
                                Imgproc.CHAIN_APPROX_SIMPLE);
                        for (int i = 0; i < contours.size(); i++) {
                            double area = Imgproc.contourArea(contours.get(i));
                            if (area> 10000.0 && area< 50000) {
                                Imgproc.drawContours(mRGBAT, contours, i, new Scalar(255, 0, 0), 5, Imgproc.LINE_8, hierarchey, 2, new Point());
                            }
                        }
                        Utils.matToBitmap(mRGBAT, bitmap);
                        ImageView imageView = findViewById(R.id.camera2_preview);
                        runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                    }
                }, this.mBackgroundHandler);
                final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

                    }
                };
                cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        try {
                            cameraCaptureSession.capture(CameraActivity.this.captureRequestBuilder.build(), captureListener, CameraActivity.this.mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        try {
                            cameraDevice.createCaptureSession(outputSurface, this, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }, this.mBackgroundHandler);
                return;

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /* access modifiers changed from: private */
    public void createCameraPreview() {
        try {
            SurfaceTexture texture = this.textureView.getSurfaceTexture();
            if (texture != null) {
                texture.setDefaultBufferSize(this.imageDimension.getWidth(), this.imageDimension.getHeight());
                Surface surface = new Surface(texture);
                CaptureRequest.Builder createCaptureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder = createCaptureRequest;
                createCaptureRequest.addTarget(surface);
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, 1);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, this.prefs.getBoolean("autoexp", true) ? 1 : 0);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, this.prefs.getBoolean("awb", true) ? 1 : 0);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, this.prefs.getBoolean("autofoc", true) ? 4 : 0);
                captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.parseLong(this.prefs.getString("exp", "33")) * 1000000);
                captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.parseInt(this.prefs.getString("iso", "800")));
                captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Float.parseFloat(this.prefs.getString("foc", "1")));
                cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        if (CameraActivity.this.cameraDevice != null) {
                            cameraCaptureSessions = cameraCaptureSession;
                            CameraActivity.this.updatePreview();
                        }
                    }

                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        Toast.makeText(CameraActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                    }
                }, null);
                return;
            }
            throw new AssertionError();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /* access modifiers changed from: private */
    public void updatePreview() {
        if (cameraDevice == null) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
        try {
            cameraCaptureSessions.setRepeatingRequest(this.captureRequestBuilder.build(), null, this.mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /* access modifiers changed from: private */
    public void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            StreamConfigurationMap map = manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
                imageDimension = new Size(imageDimension.getWidth()*720/imageDimension.getHeight(), 720);
                this.textureView.setAspectRatio(this.imageDimension.getHeight(), this.imageDimension.getWidth());
                if (ActivityCompat.checkSelfPermission(this, "android.permission.CAMERA") != 0) {
                    Log.e("Test", "No Permission");
                    ActivityCompat.requestPermissions(this, new String[]{"android.permission.CAMERA"}, 200);
                } else {
                    manager.openCamera(cameraId, this.stateCallback, null);
                }
            } else {
                throw new AssertionError();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200 && grantResults[0] != 0) {
            Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /* access modifiers changed from: protected */
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (OpenCVLoader.initDebug()) {
            Log.d("OPENCV", "OpenCV is configured or connected successfully");
            if (this.textureView.isAvailable()) {
                openCamera();
            } else {
                this.textureView.setSurfaceTextureListener(this.textureListener);
            }
        } else {
            Log.d("OPENCV", "OpenCV is not Working or Loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, null);
        }

    }

    /* access modifiers changed from: protected */
    public void onPause() {
        stopBackgroundThread();
        this.editor.putString("iso", this.iso.getText().toString());
        this.editor.putString("exp", this.exp.getText().toString());
        this.editor.putString("foc", this.foc.getText().toString());
        this.editor.apply();
        super.onPause();
    }

    private void stopBackgroundThread() {
        this.mBackgroundThread.quitSafely();
        try {
            this.mBackgroundThread.join();
            this.mBackgroundThread = null;
            this.mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        HandlerThread handlerThread = new HandlerThread("Camera Background");
        this.mBackgroundThread = handlerThread;
        handlerThread.start();
        this.mBackgroundHandler = new Handler(this.mBackgroundThread.getLooper());
    }

    private void buttons() {
        SwitchCompat exp2 = findViewById(R.id.autoexp);
        SwitchCompat focus = findViewById(R.id.autofocus);
        SwitchCompat awb = findViewById(R.id.awb);
        exp2.setChecked(this.prefs.getBoolean("autoexp", true));
        focus.setChecked(this.prefs.getBoolean("autofoc", true));
        awb.setChecked(this.prefs.getBoolean("awb", true));
        exp2.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                editor.putBoolean("autoexp", true);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, 1);
            } else {
                editor.putBoolean("autoexp", false);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, 0);
            }
            updatePreview();
        });
        focus.setOnCheckedChangeListener(((compoundButton, b) -> {
            if (b) {
                editor.putBoolean("autofoc", true);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 4);
            } else {
                editor.putBoolean("autofoc", false);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 0);
            }
            updatePreview();
        }));
        awb.setOnCheckedChangeListener(((compoundButton, b) -> {
            if (b) {
                editor.putBoolean("awb", true);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, 1);
            } else {
                editor.putBoolean("awb", false);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            }
            updatePreview();
        }));
    }


    private void texts() {
        this.iso = findViewById(R.id.edit_iso);
        this.exp = findViewById(R.id.edit_exp);
        this.foc = findViewById(R.id.edit_foc);
        this.iso.setText(this.prefs.getString("iso", "800"));
        this.exp.setText(this.prefs.getString("exp", "33"));
        this.foc.setText(this.prefs.getString("foc", "1"));
        this.iso.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void afterTextChanged(Editable editable) {
                if (!editable.toString().equals("")) {
                    captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.parseInt(editable.toString()));
                    updatePreview();
                }
            }
        });
        this.exp.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void afterTextChanged(Editable editable) {
                if (!editable.toString().equals("")) {
                    captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.parseLong(editable.toString()) * 1000000);
                    updatePreview();
                }
            }
        });
        this.foc.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void afterTextChanged(Editable editable) {
                if (!editable.toString().equals("")) {
                    CameraActivity.this.captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Float.parseFloat(editable.toString()));
                    CameraActivity.this.updatePreview();
                }
            }
        });
    }

    private void auto() {
        this.captureCallback = new CameraCaptureSession.CaptureCallback() {
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                CameraActivity.this.runOnUiThread(() -> {
                    CameraActivity.this.exp.setText(String.valueOf(result.get(CaptureResult.SENSOR_EXPOSURE_TIME) / 1000000));
                    CameraActivity.this.foc.setText(String.valueOf(result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
                    CameraActivity.this.iso.setText(String.valueOf(result.get(CaptureResult.SENSOR_SENSITIVITY)));
                });
                CameraActivity.this.updatePreview();
            }

        };
        try {
            cameraCaptureSessions.setRepeatingRequest(this.captureRequestBuilder.build(), this.captureCallback, this.mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void finish() {
        this.editor.putString("iso", this.iso.getText().toString());
        this.editor.putString("exp", this.exp.getText().toString());
        this.editor.putString("foc", this.foc.getText().toString());
        this.editor.apply();
        super.finish();
    }
}
