package com.wird.app;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** بوصلة قبلة محلية: Rotation Vector عند توفره، وAccelerometer/Magnetometer كحل احتياطي. */
public class QiblaActivity extends AppCompatActivity implements SensorEventListener {
    private static final float FILTER = 0.14f;
    private static final long UI_FRAME_MS = 45L;
    private SensorManager sensorManager;
    private Sensor rotationVector, accel, magnet;
    private final float[] gravity = new float[3];
    private final float[] geomag = new float[3];
    private boolean hasGravity, hasGeomag, usingRotationVector;
    private QiblaCompassView compass;
    private TextView info, accuracy, bearingView, headingView, alignedView;
    private double qiblaBearing;
    private float displayedRotation = Float.NaN;
    private long lastUiFrame;
    private boolean wasAligned;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_qibla);

        LinearLayout root = findViewById(R.id.qiblaRoot);
        compass = findViewById(R.id.qiblaCompass);
        info = findViewById(R.id.qiblaInfo);
        accuracy = findViewById(R.id.qiblaAccuracy);
        bearingView = findViewById(R.id.qiblaBearing);
        headingView = findViewById(R.id.qiblaHeading);
        alignedView = findViewById(R.id.qiblaAligned);
        findViewById(R.id.qiblaBack).setOnClickListener(v -> finish());

        String theme = getSharedPreferences("wird_appearance", MODE_PRIVATE)
                .getString("app_theme", "blue");
        int primary = themePrimary(theme);
        int dark = themeDark(theme);
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(dark);
        root.setBackgroundColor(themeBackground(theme));
        compass.setPrimaryColor(primary);

        double lat = getIntent().getDoubleExtra("lat", 33.5731);
        double lon = getIntent().getDoubleExtra("lon", -7.5898);
        qiblaBearing = bearingToKaaba(lat, lon);
        bearingView.setText(Math.round(qiblaBearing) + "°");

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        usingRotationVector = rotationVector != null;
        boolean available = usingRotationVector || (accel != null && magnet != null);
        compass.setSensorAvailable(available);
        if (!available) {
            accuracy.setText("لا يتوفر مستشعر بوصلة في هذا الهاتف");
            info.setText("يمكنك استعمال الاتجاه العددي: " + Math.round(qiblaBearing) + "° من الشمال");
            alignedView.setText("الاتجاه التقريبي متاح بالأرقام");
        } else {
            info.setText("حرّك الهاتف على شكل 8 إذا كانت الحركة غير مستقرة");
            accuracy.setText(usingRotationVector ? "دقة البوصلة: قيد القياس" : "دقة البوصلة: وضع احتياطي");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager == null) return;
        if (usingRotationVector) sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME);
        else {
            if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            if (magnet != null) sensorManager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        float[] rotation = new float[9];
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values);
        } else {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                lowPass(event.values, gravity); hasGravity = true;
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                lowPass(event.values, geomag); hasGeomag = true;
            }
            if (!hasGravity || !hasGeomag || !SensorManager.getRotationMatrix(rotation, null, gravity, geomag)) return;
        }

        float[] adjusted = remapForDisplay(rotation);
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjusted, orientation);
        double heading = (Math.toDegrees(orientation[0]) + 360.0) % 360.0;
        float wanted = (float) ((qiblaBearing - heading + 360.0) % 360.0);
        if (Float.isNaN(displayedRotation)) displayedRotation = wanted;
        else {
            float delta = shortest(wanted - displayedRotation);
            displayedRotation = (displayedRotation + delta * 0.22f + 360f) % 360f;
        }
        compass.setDirection(displayedRotation);

        long now = SystemClock.uptimeMillis();
        if (now - lastUiFrame < UI_FRAME_MS) return;
        lastUiFrame = now;
        headingView.setText(Math.round(heading) + "°");
        float error = Math.abs(shortest(wanted));
        boolean aligned = error <= 5f;
        compass.setAligned(aligned);
        alignedView.setText(aligned ? "✓ أنت الآن باتجاه القبلة" : "حرّك الهاتف " + turnHint(wanted));
        if (aligned && !wasAligned) compass.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        wasAligned = aligned;
        compass.setContentDescription(aligned ? "تم ضبط اتجاه القبلة" : "اتجاه القبلة " + Math.round(qiblaBearing) + " درجة");
    }

    private float[] remapForDisplay(float[] source) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (rotation == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (rotation == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        if (rotation == Surface.ROTATION_0) return source;
        float[] out = new float[9];
        SensorManager.remapCoordinateSystem(source, x, y, out);
        return out;
    }

    private static void lowPass(float[] input, float[] output) {
        for (int i = 0; i < 3; i++) output[i] += FILTER * (input[i] - output[i]);
    }

    private static float shortest(float angle) { return ((angle + 540f) % 360f) - 180f; }

    private static String turnHint(float wanted) {
        float delta = shortest(wanted);
        if (Math.abs(delta) < 15f) return "قليلاً نحو " + (delta > 0 ? "اليمين" : "اليسار");
        return delta > 0 ? "نحو اليمين" : "نحو اليسار";
    }

    @Override public void onAccuracyChanged(Sensor sensor, int value) {
        if (accuracy == null) return;
        if (value == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            accuracy.setText("دقة البوصلة: ضعيفة — حرّك الهاتف على شكل 8");
            accuracy.setTextColor(Color.parseColor("#A9473E"));
        } else if (value == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            accuracy.setText("دقة البوصلة: منخفضة");
            accuracy.setTextColor(Color.parseColor("#9A731D"));
        } else if (value == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            accuracy.setText("دقة البوصلة: جيدة");
            accuracy.setTextColor(Color.parseColor("#245F9E"));
        } else if (value == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            accuracy.setText("دقة البوصلة: ممتازة");
            accuracy.setTextColor(Color.parseColor("#0C6B59"));
        }
    }

    private static double bearingToKaaba(double lat, double lon) {
        double kaabaLat = Math.toRadians(21.422487), kaabaLon = Math.toRadians(39.826206);
        double phi = Math.toRadians(lat), lambda = Math.toRadians(lon);
        double y = Math.sin(kaabaLon - lambda);
        double x = Math.cos(phi) * Math.tan(kaabaLat) - Math.sin(phi) * Math.cos(kaabaLon - lambda);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static int themePrimary(String id) {
        switch (id == null ? "blue" : id) {
            case "green": return Color.parseColor("#0C6B59"); case "gold": return Color.parseColor("#9A731D");
            case "purple": return Color.parseColor("#7253A3"); case "burgundy": return Color.parseColor("#8B3545");
            case "teal": return Color.parseColor("#11878A"); default: return Color.parseColor("#245F9E");
        }
    }
    private static int themeDark(String id) {
        switch (id == null ? "blue" : id) {
            case "green": return Color.parseColor("#075244"); case "gold": return Color.parseColor("#6D4F0F");
            case "purple": return Color.parseColor("#4D3478"); case "burgundy": return Color.parseColor("#5D2030");
            case "teal": return Color.parseColor("#075F67"); default: return Color.parseColor("#163F73");
        }
    }
    private static int themeBackground(String id) {
        switch (id == null ? "blue" : id) {
            case "green": return Color.parseColor("#F4F1E8"); case "gold": return Color.parseColor("#F8F3E7");
            case "purple": return Color.parseColor("#F5F1F9"); case "burgundy": return Color.parseColor("#F8F1F2");
            case "teal": return Color.parseColor("#EEF7F6"); default: return Color.parseColor("#F1F5FA");
        }
    }
}
