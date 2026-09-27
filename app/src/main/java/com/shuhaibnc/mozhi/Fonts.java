package com.shuhaibnc.mozhi;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Whole-app font: Manjari (Malayalam + Latin), loaded from assets. */
public final class Fonts {

    private static Typeface regular;
    private static Typeface bold;

    private Fonts() {}

    public static Typeface get(Context c, boolean boldWant) {
        if (boldWant) {
            if (bold == null) {
                bold = Typeface.createFromAsset(c.getAssets(),
                        "fonts/Manjari-Bold.ttf");
            }
            return bold;
        }
        if (regular == null) {
            regular = Typeface.createFromAsset(c.getAssets(),
                    "fonts/Manjari-Regular.ttf");
        }
        return regular;
    }

    /** Apply Manjari to a TextView, keeping its bold-ness. */
    public static void apply(TextView tv) {
        Typeface cur = tv.getTypeface();
        boolean b = cur != null && cur.isBold();
        tv.setTypeface(get(tv.getContext(), b));
    }

    /** Recursively apply Manjari to every TextView under v. */
    public static void applyAll(View v) {
        if (v instanceof TextView) {
            apply((TextView) v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyAll(g.getChildAt(i));
            }
        }
    }
}
