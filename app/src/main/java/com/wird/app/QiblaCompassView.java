package com.wird.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** رسم خفيف للبوصلة يعمل بتسريع العتاد ولا يحتاج صوراً خارجية. */
public class QiblaCompassView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private float direction;
    private int primary = Color.rgb(36, 95, 158);
    private final int accent = Color.rgb(232, 200, 93);
    private final float density;
    private boolean aligned, sensorAvailable = true;

    public QiblaCompassView(Context context) { this(context, null); }
    public QiblaCompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    public void setPrimaryColor(int color) { primary = color; postInvalidateOnAnimation(); }
    public void setDirection(float degrees) { direction = degrees; postInvalidateOnAnimation(); }
    public void setAligned(boolean value) { if (aligned != value) { aligned = value; postInvalidateOnAnimation(); } }
    public void setSensorAvailable(boolean value) { sensorAvailable = value; postInvalidateOnAnimation(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.42f;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy - radius * .18f, radius * 1.25f,
                Color.WHITE, Color.rgb(231, 241, 250), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((aligned ? 7f : 4f) * density);
        paint.setColor(aligned ? Color.rgb(61, 171, 118) : accent);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(0x334B647D);
        canvas.drawCircle(cx, cy, radius * .76f, paint);

        canvas.save();
        canvas.translate(cx, cy);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 72; i++) {
            boolean major = i % 6 == 0;
            paint.setStrokeWidth((major ? 2.4f : .9f) * density);
            paint.setColor(major ? primary : 0x664B647D);
            canvas.drawLine(0f, -radius + 5f * density, 0f,
                    -radius + (major ? 16f : 10f) * density, paint);
            canvas.rotate(5f);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(primary);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(17f * density);
        canvas.drawText("ش", cx, cy - radius * .70f, paint);
        canvas.drawText("ج", cx, cy + radius * .80f, paint);
        canvas.drawText("ق", cx + radius * .74f, cy + 6f * density, paint);
        canvas.drawText("غ", cx - radius * .74f, cy + 6f * density, paint);

        if (sensorAvailable) {
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(direction);
            arrowPath.reset();
            arrowPath.moveTo(0f, -radius * .68f);
            arrowPath.lineTo(radius * .14f, radius * .18f);
            arrowPath.lineTo(0f, radius * .09f);
            arrowPath.lineTo(-radius * .14f, radius * .18f);
            arrowPath.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(aligned ? Color.rgb(61, 171, 118) : accent);
            canvas.drawPath(arrowPath, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(primary);
            canvas.drawPath(arrowPath, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(20, 82, 63));
            canvas.drawRoundRect(-18f * density, -radius * .74f, 18f * density,
                    -radius * .58f, 5f * density, 5f * density, paint);
            paint.setColor(accent);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            canvas.drawRoundRect(-18f * density, -radius * .74f, 18f * density,
                    -radius * .58f, 5f * density, 5f * density, paint);
            canvas.restore();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(primary);
        canvas.drawCircle(cx, cy, 17f * density, paint);
        paint.setColor(aligned ? Color.rgb(61, 171, 118) : accent);
        canvas.drawCircle(cx, cy, 8f * density, paint);
    }
}
