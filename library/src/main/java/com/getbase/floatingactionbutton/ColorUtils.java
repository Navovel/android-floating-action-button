package com.getbase.floatingactionbutton;

import android.graphics.Color;

public final class ColorUtils {

    public static int opacityToAlpha(float opacity) {
        return (int) (255f * opacity);
    }

    public static int darkenColor(int argb) {
        return adjustColorBrightness(argb, 0.9f);
    }

    public static int lightenColor(int argb) {
        return adjustColorBrightness(argb, 1.1f);
    }

    public static int adjustColorBrightness(int argb, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);

        hsv[2] = Math.min(hsv[2] * factor, 1f);

        return Color.HSVToColor(Color.alpha(argb), hsv);
    }

    public static int halfTransparent(int argb) {
        return Color.argb(
                Color.alpha(argb) / 2,
                Color.red(argb),
                Color.green(argb),
                Color.blue(argb)
        );
    }

    public static int opaque(int argb) {
        return Color.rgb(
                Color.red(argb),
                Color.green(argb),
                Color.blue(argb)
        );
    }
}
