package com.example.nds;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppList {

    public static class Item {
        public final String pkg;
        public final String label;

        Item(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    @SuppressWarnings("deprecation")
    public static List<Item> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();

        String home = null;
        ResolveInfo h = pm.resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (h != null) home = h.activityInfo.packageName;

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, Item> map = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || pkg.equals(home) || map.containsKey(pkg)) {
                continue;
            }
            map.put(pkg, new Item(pkg, ri.loadLabel(pm).toString()));
        }

        List<Item> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }
}