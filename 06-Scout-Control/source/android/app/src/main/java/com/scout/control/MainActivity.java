package com.scout.control;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity implements ScoutClient.Listener {
    private static final int BG = 0xff0c1713, CARD = 0xff17291f, TEXT = 0xffedf6ef;
    private static final int MUTED = 0xff91ab9c, MINT = 0xffa3e9c0, STOP = 0xffedb296;
    private ScoutClient client;
    private JoystickView joystick;
    private TextView statusTitle, statusDetail, leftPower, rightPower, speedValue;
    private Button connect, calibration;
    private final Button[] faceButtons = new Button[11];
    private TextView faceStatus;
    private LinearLayout root;
    private boolean resumed;
    private boolean permissionForCalibration;
    private final Handler main = new Handler();
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (resumed && hasWindowFocus()) client.state.heartbeat(SystemClock.elapsedRealtime());
            changed(); main.postDelayed(this, 50);
        }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        client = new ScoutClient(getApplicationContext(), this); build(); main.post(heartbeat);
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setIncludeFontPadding(false); if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams size(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private void gap(LinearLayout v, int h) { v.addView(new View(this), size(1,h)); }
    private Button button(String value, int color, int foreground) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(foreground); b.setBackground(shape(color, 0, 14)); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(12), 0, dp(12), 0); b.setStateListAnimator(null); return b;
    }
    private void build() {
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        root = column(); root.setBackgroundColor(BG); root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int l, t, r, b;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l=safe.left; t=safe.top; r=safe.right; b=safe.bottom;
            } else { l=insets.getSystemWindowInsetLeft(); t=insets.getSystemWindowInsetTop(); r=insets.getSystemWindowInsetRight(); b=insets.getSystemWindowInsetBottom(); }
            root.setPadding(dp(18)+l, dp(wide ? 8 : 12)+t, dp(18)+r, dp(10)+b); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout header = row();
        LinearLayout branding = column();
        TextView brand = text("SCOUT", 25, TEXT, true); brand.setLetterSpacing(.16f); branding.addView(brand);
        TextView sub = text("A LITTLE EXPLORER. IN YOUR HANDS.", 9, MUTED, true); sub.setLetterSpacing(.10f);
        branding.addView(sub, size(-2, -2)); header.addView(branding, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text("LOCAL\nCONTROL", 9, MINT, true); badge.setGravity(Gravity.CENTER); badge.setLetterSpacing(.08f);
        badge.setBackground(shape(CARD, 0xff34523f, 10)); badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        header.addView(badge); root.addView(header, size(-1, wide ? 48 : 58));
        LinearLayout content = wide ? row() : column();
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = column();
        if (wide) {
            LinearLayout pad = column(); joystick = new JoystickView(this, client::input);
            pad.addView(joystick, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView hint = text("DRAG TO EXPLORE  ·  RELEASE TO STOP", 9, MUTED, true); hint.setGravity(Gravity.CENTER);
            pad.addView(hint, size(-1, 20)); content.addView(pad, new LinearLayout.LayoutParams(0, -1, 1.1f));
            View spacer = new View(this); content.addView(spacer, size(18, 1));
            content.addView(controls, new LinearLayout.LayoutParams(0, -1, 1));
            ScrollView scroller = new ScrollView(this); scroller.setFillViewport(false); scroller.setVerticalScrollBarEnabled(false);
            LinearLayout upper = column(); scroller.addView(upper); controls.addView(scroller, new LinearLayout.LayoutParams(-1,0,1));
            connectionCard(upper, true); gap(upper, 8); speedControl(upper, true); gap(upper, 6); powerControl(upper, true);
            gap(upper, 8); faceControls(upper);
            gap(controls, 6); stopButton(controls, 48); footer(controls, true);
        } else {
            connectionCard(content, false);
            joystick = new JoystickView(this, client::input);
            content.addView(joystick, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView hint = text("DRAG TO EXPLORE  ·  RELEASE TO STOP", 10, MUTED, true); hint.setGravity(Gravity.CENTER); hint.setLetterSpacing(.06f);
            content.addView(hint, size(-1, 24));
            speedControl(content, false); gap(content, 8); powerControl(content, false); gap(content, 8); faceControls(content); gap(content, 8); stopButton(content, 58); footer(content, false);
        }
        changed();
    }
    private void connectionCard(LinearLayout parent, boolean compact) {
        LinearLayout card = column(); card.setBackground(shape(CARD, 0xff2b4335, 16)); card.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout top = row();
        statusTitle = text("○  Disconnected", compact ? 13 : 15, TEXT, true);
        top.addView(statusTitle, new LinearLayout.LayoutParams(0, -2, 1));
        connect = button("Connect", MINT, 0xff143522); connect.setOnClickListener(v -> beginConnection(false)); top.addView(connect, size(88, compact ? 34 : 38));
        card.addView(top, size(-1,-2));
        statusDetail = text("Join C3-Scout-Test to get started", 11, MUTED, false); statusDetail.setMaxLines(3);
        LinearLayout.LayoutParams detail = size(-1,-2); detail.topMargin=dp(5); card.addView(statusDetail, detail);
        parent.addView(card, size(-1,-2));
    }
    private void speedControl(LinearLayout parent, boolean compact) {
        LinearLayout title = row(); TextView label = text("DRIVE LIMIT", 10, MUTED, true); label.setLetterSpacing(.12f);
        title.addView(label, new LinearLayout.LayoutParams(0,-2,1));
        speedValue = text("35%", compact ? 18 : 22, MINT, true); title.addView(speedValue); parent.addView(title, size(-1, compact ? 24 : 28));
        SeekBar seek = new SeekBar(this); seek.setMax(100); seek.setProgress(client.state.cap);
        seek.setContentDescription("Speed limit, percent"); seek.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(MINT)); seek.setPadding(dp(8),0,dp(8),0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) { if (user) client.speed(value); }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        }); parent.addView(seek, size(-1, compact ? 30 : 38));
        parent.addView(text("0–30% off  ·  motor power 40–100%", 10, MUTED, false), size(-1,18));
    }
    private void powerControl(LinearLayout parent, boolean compact) {
        LinearLayout powers = row(); powers.setBackground(shape(CARD, 0, 13)); powers.setPadding(dp(14),dp(7),dp(14),dp(7));
        leftPower = text("L  0%", compact ? 17 : 20, TEXT, true); rightPower = text("R  0%", compact ? 17 : 20, TEXT, true); rightPower.setGravity(Gravity.RIGHT);
        leftPower.setContentDescription("Left commanded motor power zero percent");
        powers.addView(leftPower, new LinearLayout.LayoutParams(0,-2,1)); powers.addView(text("MOTOR TARGET", 8, MUTED, true));
        powers.addView(rightPower, new LinearLayout.LayoutParams(0,-2,1)); parent.addView(powers, size(-1, compact ? 42 : 50));
    }
    private void stopButton(LinearLayout parent, int height) {
        Button stop = button("■   STOP", STOP, 0xff40271c); stop.setTextSize(19); stop.setLetterSpacing(.10f);
        stop.setContentDescription("Stop both motors and disconnect"); stop.setOnClickListener(v -> { joystick.reset(); client.stop("Stopped · reconnect to drive"); });
        parent.addView(stop, size(-1,height));
    }
    private void faceControls(LinearLayout parent) {
        LinearLayout heading = row();
        TextView title = text("SCOUT FACE", 10, MUTED, true);
        heading.addView(title, size(84, 22));
        faceStatus = text("Connect to choose a face", 10, MUTED, false);
        faceStatus.setSingleLine(true);
        faceStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(faceStatus, new LinearLayout.LayoutParams(0, dp(22), 1));
        parent.addView(heading, size(-1, 22));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(true);
        scroller.setContentDescription("Choose Scout face; swipe for more expressions");
        LinearLayout choices = row();
        for (int index = 0; index < faceButtons.length; ++index) {
            final int id = index - 1;
            Button choice = button(id == -1 ? "Auto" : FaceSelection.NAMES[id], CARD, TEXT);
            choice.setTextSize(12);
            choice.setContentDescription(id == -1 ? "Automatically cycle all faces" : "Choose " + FaceSelection.NAMES[id] + " face");
            choice.setOnClickListener(v -> client.selectFace(id));
            LinearLayout.LayoutParams params = size(-2, 44);
            params.rightMargin = dp(6);
            choices.addView(choice, params); faceButtons[index] = choice;
        }
        scroller.addView(choices);
        parent.addView(scroller, size(-1, 48));
    }
    private void footer(LinearLayout parent, boolean compact) {
        LinearLayout footer = row();
        Button wifi = button("Wi-Fi settings ↗", BG, MUTED); wifi.setTextSize(11); wifi.setOnClickListener(v -> {
            joystick.reset(); client.stop("Wi-Fi settings · reconnect to drive"); startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });
        calibration = button("Motor directions", BG, MUTED); calibration.setTextSize(11); calibration.setOnClickListener(v -> beginConnection(true));
        footer.addView(wifi, new LinearLayout.LayoutParams(0,-1,1)); footer.addView(calibration, new LinearLayout.LayoutParams(0,-1,1));
        parent.addView(footer, size(-1,compact ? 36 : 42));
    }
    @Override public void changed() {
        if (joystick == null || client == null) return;
        synchronized (client.state) {
            SessionState s = client.state; boolean active = s.active(); joystick.ready(active);
            statusTitle.setText(active ? "●  Connected" : s.mode == SessionState.WAITING_WIFI ? "◌  Joining Scout…" : s.mode == SessionState.CONNECTING ? "◌  Connecting…" : s.mode == SessionState.ERROR ? "○  Connection ended" : "○  Disconnected");
            statusTitle.setTextColor(active ? MINT : s.mode == SessionState.ERROR ? STOP : TEXT);
            statusDetail.setText(s.message); speedValue.setText(s.cap + "%");
            leftPower.setText("L  " + signed(s.left)); rightPower.setText("R  " + signed(s.right));
            leftPower.setContentDescription("Left commanded motor power " + s.left + " percent");
            rightPower.setContentDescription("Right commanded motor power " + s.right + " percent");
            connect.setEnabled(!active && s.mode != SessionState.CONNECTING && s.mode != SessionState.WAITING_WIFI && !client.busy()); connect.setAlpha(connect.isEnabled() ? 1f : .45f);
            calibration.setEnabled(!active && s.mode != SessionState.CONNECTING && s.mode != SessionState.WAITING_WIFI && !client.busy()); calibration.setAlpha(calibration.isEnabled() ? 1f : .35f);
            if (faceStatus != null) {
                FaceSelection f = client.faces;
                faceStatus.setText(active ? f.message : "Connect to choose a face");
                for (int i = 0; i < faceButtons.length; ++i) {
                    Button b = faceButtons[i]; if (b == null) continue;
                    boolean enabled = active && f.supported && f.available;
                    b.setEnabled(enabled); b.setAlpha(enabled ? 1f : .35f);
                    boolean selected = active && i - 1 == f.selected;
                    if (b.isSelected() != selected) {
                        b.setSelected(selected);
                        b.setBackground(shape(selected ? MINT : CARD, 0, 14));
                        b.setTextColor(selected ? 0xff143522 : TEXT);
                    }
                }
            }
        }
    }
    private String signed(int power) { return (power > 0 ? "+" : "") + power + "%"; }
    private void beginConnection(boolean calibrate) {
        permissionForCalibration = calibrate;
        if (Build.VERSION.SDK_INT >= 29) {
            String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.NEARBY_WIFI_DEVICES : Manifest.permission.ACCESS_FINE_LOCATION;
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                String[] requested = Build.VERSION.SDK_INT >= 33 ? new String[] {permission} : new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, permission};
                requestPermissions(requested, 41); return;
            }
            if (Build.VERSION.SDK_INT < 33 && !((LocationManager) getSystemService(LOCATION_SERVICE)).isLocationEnabled()) {
                new AlertDialog.Builder(this).setTitle("Enable Wi-Fi device connections")
                    .setMessage("This Android version requires Location to be on to connect directly to Scout. Scout does not read your GPS location.")
                    .setPositiveButton("Open settings", (d,w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancel", null).show(); return;
            }
        }
        if (calibrate) client.inspectCalibration(); else client.connect();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 41) {
            String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.NEARBY_WIFI_DEVICES : Manifest.permission.ACCESS_FINE_LOCATION;
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) beginConnection(permissionForCalibration);
            else client.stop("Wi-Fi permission is needed to connect to Scout");
        }
    }
    private boolean waitingForWifi() { synchronized (client.state) { return client.state.mode == SessionState.WAITING_WIFI; } }
    @Override public void calibration(boolean left, boolean right) {
        LinearLayout body = column(); body.setPadding(dp(22),dp(8),dp(22),dp(8));
        TextView info = text("Flip a motor's direction if forward turns that wheel backward. Scout stays stopped while directions are saved.", 14, MUTED, false);
        body.addView(info); gap(body,16);
        Switch l = new Switch(this); l.setText("Reverse left motor"); l.setTextColor(TEXT); l.setChecked(left); body.addView(l,size(-1,48));
        Switch r = new Switch(this); r.setText("Reverse right motor"); r.setTextColor(TEXT); r.setChecked(right); body.addView(r,size(-1,48));
        new AlertDialog.Builder(this).setTitle("Motor directions").setView(body).setNegativeButton("Cancel",null)
            .setPositiveButton("Save directions",(dialog, which) -> client.configure(l.isChecked(), r.isChecked())).show();
    }
    @Override protected void onResume() { super.onResume(); resumed = true; }
    @Override protected void onPause() {
        resumed = false; if (client != null && !waitingForWifi()) client.leave("App paused · reconnect to drive"); if (joystick != null) joystick.reset(); super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus && client != null) {
            synchronized (client.state) {
                if (client.state.mode == SessionState.ACTIVE || client.state.mode == SessionState.CONNECTING)
                    client.stop("Focus lost · reconnect to drive");
            }
            if (joystick != null) joystick.reset();
        }
    }
    @Override protected void onStop() { if (client != null) client.leave("App hidden · reconnect to drive"); super.onStop(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration); client.stop("Layout changed · reconnect to drive"); build();
    }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null); if (client != null) client.close(); super.onDestroy();
    }
}
