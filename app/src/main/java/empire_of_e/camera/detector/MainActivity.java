package empire_of_e.camera.detector;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import android.widget.ProgressBar;
import android.transition.Visibility;

public class MainActivity extends Activity {

    private ListView wifiList;
    private WifiManager wifiManager;

		Activity me;
    private final int MY_PERMISSIONS_ACCESS_COARSE_LOCATION = 1;

    WifiReceiver receiverWifi;
		Button buttonScan ;
		Boolean running = false;

		private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    ProgressBar pb;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
				me = this;
				pb = findViewById(R.id.pb);
		showProgress(false);
        wifiList = findViewById(R.id.wifiList);


        textureView = findViewById(R.id.surface);
				assert textureView != null;
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {
            //Toast.makeText(getApplicationContext(), "Turning WiFi ON...", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

				wifiList.setOnItemClickListener(new ListView.OnItemClickListener(){

								@Override
								public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
										String[] data = p1.getItemAtPosition(p3).toString().split("\n");
										String SSID = data[0].split(":")[1].trim();

										Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com/search?q=" + URLEncoder.encode(SSID)));
										startActivity(browserIntent);
										//Toast.makeText(MainActivity.this,data.getText().toString(),Toast.LENGTH_LONG).show();

								}



						});


        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)
						!= PackageManager.PERMISSION_GRANTED) {
						ActivityCompat.requestPermissions(
								MainActivity.this,
								new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_ACCESS_COARSE_LOCATION
						);
				}
				else {
						if (!running) {
								running = true;

								new Thread(new Runnable(){
												@Override
												public void run() {

														List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
														wifiManager.disconnect();
														for (WifiConfiguration config : configs) {
																wifiManager.removeNetwork(config.networkId);
																wifiManager.saveConfiguration();
														}
														wifiManager.startScan();
														SystemClock.sleep(5000);
														while (running) {
																if (wifiManager.startScan() == true) {
																		
																		SystemClock.sleep(5000);
																		
																}
																else {
																		showProgress(true);
																		wifiManager.disconnect();
																		wifiManager.setWifiEnabled(false);
																		wifiManager.setWifiEnabled(true);
																		while(wifiManager.startScan() != true) {
																		}
																		SystemClock.sleep(5000);
																		showProgress(false);
																}
														}

														for (WifiConfiguration config : configs) {
																wifiManager.addNetwork(config);
																wifiManager.saveConfiguration();
														}
												}
										}).start();
						}
				}
    }
		
		public void showProgress(final boolean shouldShow)
		{
				me.runOnUiThread(new Runnable(){
								@Override
								public void run() {
										int vis = 0;
										if(shouldShow)
										{
												vis = pb.VISIBLE;
										}
										else
										{
												vis = pb.GONE;
										}
										pb.setVisibility(vis);
								}
						});
				
		}


		TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
						try {
								openCamera();
						}
						catch (Exception e) {
								//
						}
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
						return false;
        }


        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
						try {
								Log.d("camera", "onOpened");
								cameraDevice = camera;
								createCameraPreview();
						}
						catch (Exception e) {
								//
						}
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
						cameraDevice.close();
						cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,  CaptureRequest request,  TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
						createCameraPreview();
        }
    };
    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
				catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getHeight(), imageDimension.getWidth());

            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
										@Override
										public void onConfigured(CameraCaptureSession cameraCaptureSession) {
												//The camera is already closed
												if (null == cameraDevice) {
														return;
												}
												// When the session is ready, we start displaying the preview.
												cameraCaptureSessions = cameraCaptureSession;
												updatePreview();
										}
										@Override
										public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
												Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
										}
								}, null);
        }
				catch (CameraAccessException e) {

        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        Log.e("camera", "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        }
				catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e("camera", "openCamera X");
    }



    protected void updatePreview() {


        if (null == cameraDevice) {
            Log.e("camera", "updatePreview error, return");
        }
				captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

				try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        }
				catch (CameraAccessException e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

    }


    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        Log.e("camera", "onResume");
        startBackgroundThread();

        if (textureView.isAvailable()) {
						openCamera();
        }
				else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }




    @Override
    protected void onPostResume() {
        super.onPostResume();
        receiverWifi = new WifiReceiver(wifiManager, wifiList);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(receiverWifi, intentFilter);
        getWifi();
    }
		public void toast(final String mess) {
				me.runOnUiThread(new Runnable(){

								@Override
								public void run() {
										Toast.makeText(me, mess, Toast.LENGTH_LONG).show();
								}
						});
		}

    private void getWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						//    Toast.makeText(MainActivity.this, "version>=marshmallow", Toast.LENGTH_SHORT).show();
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
								// Toast.makeText(MainActivity.this, "location turned off", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA}, MY_PERMISSIONS_ACCESS_COARSE_LOCATION);
            }
						else {
								//  Toast.makeText(MainActivity.this, "location turned on", Toast.LENGTH_SHORT).show();
                wifiManager.startScan();
            }
        }
				else {
						//   Toast.makeText(MainActivity.this, "scanning", Toast.LENGTH_SHORT).show();
            wifiManager.startScan();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
				Log.e("camera", "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
        unregisterReceiver(receiverWifi);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
										//     Toast.makeText(MainActivity.this, "permission granted", Toast.LENGTH_SHORT).show();
                    wifiManager.startScan();
                }
								else {

										Toast.makeText(MainActivity.this, "permission not granted", Toast.LENGTH_SHORT).show();
                    return;
                }

                break;
        }
    }
}
