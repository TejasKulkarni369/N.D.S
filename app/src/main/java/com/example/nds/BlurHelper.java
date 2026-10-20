package com.example.nds;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class BlurHelper {

    // blur the screen behind a dialog card; the blur goes away when the card closes
    public static void blurBehind(View root, Dialog dialog) {
        if (root == null) return;

        if (Build.VERSION.SDK_INT >= 31) {
            root.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP));
            dialog.setOnDismissListener(d -> root.setRenderEffect(null));
            return;
        }

        // older phones: blur a small copy of the screen and lay it on top
        if (!(root instanceof ViewGroup)) return;
        Bitmap blurred = blurredCopy(root);
        if (blurred == null) return;

        ImageView veil = new ImageView(root.getContext());
        veil.setImageBitmap(blurred);
        veil.setScaleType(ImageView.ScaleType.FIT_XY);
        ((ViewGroup) root).addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setOnDismissListener(d -> ((ViewGroup) root).removeView(veil));
    }

    private static Bitmap blurredCopy(View v) {
        int w = v.getWidth();
        int h = v.getHeight();
        if (w <= 0 || h <= 0) return null;

        int bw = Math.max(1, w / 8);
        int bh = Math.max(1, h / 8);
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(bw / (float) w, bh / (float) h);
        v.draw(c);

        boxBlur(bmp, 3);
        boxBlur(bmp, 3);
        return bmp;
    }

    private static void boxBlur(Bitmap bmp, int r) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        bmp.getPixels(a, 0, w, 0, 0, w, h);
        pass(a, b, w, h, r, true);
        pass(b, a, w, h, r, false);
        bmp.setPixels(a, 0, w, 0, 0, w, h);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int div = 2 * r + 1;

        for (int l = 0; l < lines; l++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[idx(l, clamp(i, 0, len - 1), w, horizontal)];
                sa += p >>> 24;
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                dst[idx(l, i, w, horizontal)] = ((sa / div) << 24) | ((sr / div) << 16)
                        | ((sg / div) << 8) | (sb / div);

                int po = src[idx(l, clamp(i - r, 0, len - 1), w, horizontal)];
                int pi = src[idx(l, clamp(i + r + 1, 0, len - 1), w, horizontal)];
                sa += (pi >>> 24) - (po >>> 24);
                sr += ((pi >> 16) & 0xFF) - ((po >> 16) & 0xFF);
                sg += ((pi >> 8) & 0xFF) - ((po >> 8) & 0xFF);
                sb += (pi & 0xFF) - (po & 0xFF);
            }
        }
    }
}