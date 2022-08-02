package in.testright.watertag;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCamera2View;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "CameraActivity";

    JavaCameraView javaCameraView;
    Mat mRGBA, mRGBAT;
    BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(CameraActivity.this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == SUCCESS) {
                javaCameraView.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        javaCameraView = findViewById(R.id.camera_view);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
            javaCameraView.setCameraPermissionGranted();
        }
        else {
            javaCameraView.setCameraPermissionGranted();
        }
        javaCameraView.setVisibility(View.VISIBLE);
        javaCameraView.setCvCameraViewListener(CameraActivity.this);

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRGBA = new Mat(width, height, CvType.CV_8UC4);

    }


    @Override
    public void onCameraViewStopped() {
        mRGBA.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRGBA = inputFrame.rgba();

        mRGBAT = mRGBA.t();
        Core.flip(mRGBA.t(), mRGBAT, 1);
        Imgproc.resize(mRGBAT, mRGBAT, mRGBA.size());
        Mat blur = mRGBAT.clone();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size((2*2) + 1, (2*2)+1));
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
        return mRGBAT;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV is configured or connected successfully");
            baseLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        } else {
            Log.d(TAG, "OpenCV is not Working or Loaded");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, baseLoaderCallback);
        }
    }


}