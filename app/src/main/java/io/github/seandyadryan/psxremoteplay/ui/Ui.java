package io.github.seandyadryan.psxremoteplay.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(13, 20, 32), CARD = Color.rgb(24, 35, 51);
    static final int TEXT = Color.rgb(237, 244, 250), MUTED = Color.rgb(165, 183, 202);
    static final int ACCENT = Color.rgb(140, 245, 205);
    private Ui() { }
    static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Activity activity) {
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    static void insets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }
    static LinearLayout card(Activity activity, LinearLayout parent) {
        LinearLayout box = column(activity); int pad = dp(activity, 18); box.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable(); background.setColor(CARD); background.setCornerRadius(dp(activity, 20));
        box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(activity, 16);
        parent.addView(box, params); return box;
    }
    static TextView text(Activity activity, ViewGroup parent, String value, int size, int color, boolean bold) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(text, new ViewGroup.LayoutParams(-1, -2)); return text;
    }
    static Button button(Activity activity, ViewGroup parent, String label, boolean primary, View.OnClickListener action) {
        Button button = new Button(activity); button.setText(label); button.setAllCaps(false);
        button.setTextColor(primary ? BG : ACCENT); button.setTextSize(14); button.setMinHeight(dp(activity, 48));
        GradientDrawable background = new GradientDrawable(); background.setColor(primary ? ACCENT : CARD);
        background.setCornerRadius(dp(activity, 12));
        if (!primary) background.setStroke(dp(activity, 1), Color.rgb(63, 89, 104));
        button.setBackground(background); button.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(activity, 8);
        parent.addView(button, params); button.setOnClickListener(action); return button;
    }
}
