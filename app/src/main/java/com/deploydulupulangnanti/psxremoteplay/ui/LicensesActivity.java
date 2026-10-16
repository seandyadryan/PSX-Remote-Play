package com.deploydulupulangnanti.psxremoteplay.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class LicensesActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); frame.addView(scroll);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this,24); page.setPadding(pad,pad,pad,pad); scroll.addView(page);
        Ui.text(this,page,"Component licenses",26,Ui.TEXT,true);
        Ui.text(this,page,"The connection engine is built from pinned public sources. App and component licenses are listed separately.",14,Ui.MUTED,false);
        Ui.button(this,page,"Source and build instructions",true,view -> show("NATIVE_SOURCE_NOTICE.txt"));
        try {
            String[] files = getAssets().list("licenses");
            if (files != null) for (String file : files) Ui.button(this,page,file,false,view -> show("licenses/" + file));
        } catch (IOException error) { Ui.text(this,page,"Unable to read the license list.",14,Ui.MUTED,false); }
        Ui.button(this,page,"Back",false,view -> finish());
        setContentView(frame); Ui.insets(frame);
    }
    private void show(String file) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(file),StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) text.append(line).append('\n');
        } catch (IOException error) { text.append("Unable to read the license document."); }
        TextView content = new TextView(this); content.setText(text); content.setTextIsSelectable(true);
        int padding = Ui.dp(this,16); content.setPadding(padding,padding,padding,padding);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(file).setView(scroll).setPositiveButton("Close",null).show();
    }
}
