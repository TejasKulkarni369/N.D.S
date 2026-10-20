package com.example.nds;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import androidx.core.content.ContextCompat;

public class LockedIcon {

    // grey + dim app logo with a red solid lock badge in the corner
    public static Drawable of(Context ctx, Drawable appIcon) {
        Drawable base = appIcon;
        if (appIcon.getConstantState() != null) {
            base = appIcon.getConstantState().newDrawable().mutate();
        }

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        base.setColorFilter(new ColorMatrixColorFilter(cm));
        base.setAlpha(120);

        Drawable lock = ContextCompat.getDrawable(ctx, R.drawable.ic_lock_badge);
        if (lock == null) return base;

        int w = base.getIntrinsicWidth();
        int h = base.getIntrinsicHeight();
        if (w <= 1) w = 96;
        if (h <= 1) h = 96;

        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, lock});
        layers.setLayerSize(1, w / 2, h / 2);
        layers.setLayerGravity(1, Gravity.BOTTOM | Gravity.END);
        return layers;
    }
}