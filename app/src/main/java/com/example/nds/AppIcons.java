package com.example.nds;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

// app icons are decoded once and kept (scrolling a list no longer decodes them again)
public class AppIcons {

    private static final LruCache<String, Drawable.ConstantState> CACHE = new LruCache<>(60);

    public static Drawable get(Context c, String pkg) {
        Drawable.ConstantState cs = CACHE.get(pkg);
        if (cs != null) return cs.newDrawable(c.getResources());
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            Drawable.ConstantState st = d.getConstantState();
            if (st != null) {
                CACHE.put(pkg, st);
                return st.newDrawable(c.getResources());
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }
}
