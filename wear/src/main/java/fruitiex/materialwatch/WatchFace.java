package fruitiex.materialwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.util.Calendar;
import java.util.TimeZone;

public class WatchFace extends CanvasWatchFaceService {
    static Paint secondPaint;
    static Paint handsPaint;
    static Paint ambientPaint;
    static Paint tickPaint;
    static Paint bgPaint;
    static Paint outerPaint;
    static Paint shadowPaint;
    static int outerOuterBgColor;
    static boolean enableTicks;
    static boolean enableShadows;

    static float thickStroke = 6.0f;
    static float thinStroke = 2.0f;
    static float circleOffs = 24.0f;

    static float minHrOverflow = 10.0f;
    static float secOverflow = 16.0f;

    // device screen details
    boolean mLowBitAmbient;
    boolean mBurnInProtection;

    private static final long INTERACTIVE_UPDATE_RATE_MS = 33;

    static Values val;

    static Bitmap shadowBitmap = null;

    public static void resetColors() {
        int s = val.getColor("SecondHand");
        int l = val.getColor("Hands");
        int a = val.getColor("AmbientColor");
        int t = val.getColor("Ticks");
        int i = val.getColor("InnerBG");
        int o = val.getColor("OuterBG");
        int q = val.getColor("SquareBG");

        handsPaint = new Paint();
        handsPaint.setARGB(255, Color.red(l), Color.green(l), Color.blue(l));
        handsPaint.setStyle(Paint.Style.STROKE);
        handsPaint.setStrokeWidth(thickStroke);
        handsPaint.setAntiAlias(true);

        ambientPaint = new Paint();
        ambientPaint.setARGB(255, Color.red(a), Color.green(a), Color.blue(a));
        ambientPaint.setStyle(Paint.Style.STROKE);
        ambientPaint.setStrokeWidth(thinStroke);
        ambientPaint.setAntiAlias(true);

        tickPaint = new Paint();
        tickPaint.setARGB(255, Color.red(t), Color.green(t), Color.blue(t));
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(thinStroke);
        tickPaint.setAntiAlias(true);

        secondPaint = new Paint();
        secondPaint.setARGB(255, Color.red(s), Color.green(s), Color.blue(s));
        secondPaint.setStyle(Paint.Style.STROKE);
        secondPaint.setStrokeWidth(thinStroke);
        secondPaint.setAntiAlias(true);

        bgPaint = new Paint();
        bgPaint.setARGB(255, Color.red(i), Color.green(i), Color.blue(i));
        bgPaint.setAntiAlias(true);

        outerPaint = new Paint();
        outerPaint.setARGB(255, Color.red(o), Color.green(o), Color.blue(o));
        outerPaint.setAntiAlias(true);

        shadowPaint = new Paint();
        shadowPaint.setARGB(242, 0, 0, 0);
        shadowPaint.setAntiAlias(true);

        outerOuterBgColor = Color.rgb(Color.red(q), Color.green(q), Color.blue(q));

        enableTicks = val.getBoolean("EnableTicks");
        enableShadows = val.getBoolean("EnableShadows");

        shadowBitmap = null;
    }

    @Override
    public Engine onCreateEngine() {
        val = new Values(getApplicationContext());
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;
        Calendar mCalendar;
        static final float TWO_PI = (float) Math.PI * 2f;

                /** Handler to update the time once a second in interactive mode. */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION,
                    false);
        }
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            invalidate();

            updateTimer();
        }
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setStatusBarGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
                    .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT)
                    .setShowSystemUiTime(false)
                    .build());

            resetColors();

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private void drawTicks(Canvas canvas, Rect bounds, Paint paint) {
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float innerX, innerY, outerX, outerY;

            // draw the ticks/dials
            float innerTickRadius = centerX - circleOffs - 24;
            float outerTickRadius = centerX - circleOffs - 22;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                innerX = (float) Math.sin(tickRot) * (innerTickRadius - (tickIndex % 3 == 0 ? 6 : 0));
                innerY = (float) -Math.cos(tickRot) * (innerTickRadius - (tickIndex % 3 == 0 ? 6 : 0));
                outerX = (float) Math.sin(tickRot) * outerTickRadius;
                outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(centerX + innerX, centerY + innerY,
                        centerX + outerX, centerY + outerY, paint);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            canvas.drawColor(Color.BLACK);

            int width = bounds.width();
            int height = bounds.height();

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            mCalendar.setTimeInMillis(System.currentTimeMillis());

            // draw the clock pointers
            float seconds =
                    mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f;
            float secRot = seconds / 60f * TWO_PI;
            float minutes = mCalendar.get(Calendar.MINUTE) + seconds / 60f;
            float minRot = minutes / 60f * TWO_PI;
            float hours = mCalendar.get(Calendar.HOUR) + minutes / 60f;
            float hrRot = hours / 12f * TWO_PI;

            float secLength = centerX - 60;
            float minLength = centerX - 65;
            float hrLength = centerX - 95;

            if (!isInAmbientMode()) {
                // draw background
                canvas.drawColor(outerOuterBgColor);

                canvas.drawCircle(centerX, centerY, width / 2, outerPaint);

                if (shadowBitmap == null) {
                    // draw shadow of inner background
                    shadowBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas shadowCanvas = new Canvas(shadowBitmap);
                    shadowCanvas.drawCircle(centerX, centerY, width / 2 - circleOffs - 20.0f, shadowPaint);

                    /*
                    if (enableTicks) {
                        drawTicks(shadowCanvas, bounds, shadowPaint);
                    }
                    */

                    // blur shadow
                    RenderScript rs = RenderScript.create(getApplicationContext());
                    ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
                    Allocation inAlloc = Allocation.createFromBitmap(rs, shadowBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_GRAPHICS_TEXTURE);
                    Allocation outAlloc = Allocation.createFromBitmap(rs, shadowBitmap);
                    script.setRadius(20);
                    script.setInput(inAlloc);
                    script.forEach(outAlloc);
                    outAlloc.copyTo(shadowBitmap);
                    rs.destroy();
                }

                if (enableShadows) {
                    canvas.drawBitmap(shadowBitmap, 0, 6, null);
                }

                // draw inner background
                canvas.drawCircle(centerX, centerY, width / 2 - circleOffs - 16.0f, bgPaint);

                // draw second hand
                float secX = (float) Math.sin(secRot);
                float secY = (float) -Math.cos(secRot);
                canvas.drawLine(centerX - secX * secOverflow, centerY - secY * secOverflow, centerX + secX * secLength, centerY + secY * secLength, secondPaint);

                float minX = (float) Math.sin(minRot);
                float minY = (float) -Math.cos(minRot);
                canvas.drawLine(centerX - minX * minHrOverflow, centerY - minY * minHrOverflow, centerX + minX * minLength, centerY + minY * minLength, handsPaint);

                float hrX = (float) Math.sin(hrRot);
                float hrY = (float) -Math.cos(hrRot);
                canvas.drawLine(centerX - hrX * minHrOverflow, centerY - hrY * minHrOverflow, centerX + hrX * hrLength, centerY + hrY * hrLength, handsPaint);
            } else {
                // floor minute hand
                minRot = (float) Math.floor(minutes) / 60f * TWO_PI;

                float minX = (float) Math.sin(minRot);
                float minY = (float) -Math.cos(minRot);
                canvas.drawLine(centerX - minX * minHrOverflow, centerY - minY * minHrOverflow, centerX + minX * minLength, centerY + minY * minLength, ambientPaint);

                float hrX = (float) Math.sin(hrRot);
                float hrY = (float) -Math.cos(hrRot);
                canvas.drawLine(centerX - hrX * minHrOverflow, centerY - hrY * minHrOverflow, centerX + hrX * hrLength, centerY + hrY * hrLength, ambientPaint);

                canvas.drawCircle(centerX, centerY, width / 2 - circleOffs - 16.0f, ambientPaint);
            }


            if (enableTicks) {
                drawTicks(canvas, bounds, tickPaint);
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
