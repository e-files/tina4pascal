package com.tina4.pascal;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Loads assets/controls.html and hands it to the native renderer. */
public class MainActivity extends Activity {

    private static MainActivity instance;

    private static final int REQ_PICK_FILE = 42;
    private static final int REQ_CAPTURE   = 43;
    private static final int REQ_CAMERA    = 4711;   // Tina4Scanner.ensurePermission
    private static final int REQ_MIC       = 4713;   // <recorder> RECORD_AUDIO
    private Tina4View view;
    private MediaRecorder recorder;                  // live <recorder> capture, null when idle
    private final android.os.Handler levelHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable levelPoll;                       // E1: polls getMaxAmplitude → view.pushAudioLevel
    private String recPath;
    private boolean pendingRecord;                   // waiting on the mic permission dialog

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        Tina4Share.init(this);
        Tina4Location.init(this);
        // local notifications: hold the app context + channel; ask permission on 33+
        Tina4Notify.init(this);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 4712);
        }
        // No ActionBar (theme). Keep the system bars visually edge-to-edge, but
        // place the engine view inside their safe rectangle. Android 15/16
        // enforces edge-to-edge for current target SDKs, so relying on the old
        // decor-fit behavior lets a fixed footer slide under the nav bar.
        getWindow().setStatusBarColor(0xFFFBFAF7);
        getWindow().setNavigationBarColor(0xFFFFFDF7);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        view = new Tina4View(this);
        Tina4Location.setView(view);
        Tina4Share.setView(view);
        // Host the engine view in a FrameLayout so native <video> players can be
        // overlaid as sibling views positioned over their poster boxes.
        FrameLayout root = new FrameLayout(this);
        FrameLayout.LayoutParams safeLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(view, safeLp);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
            if (lp.leftMargin != left || lp.topMargin != top ||
                lp.rightMargin != right || lp.bottomMargin != bottom) {
                lp.leftMargin = left; lp.topMargin = top;
                lp.rightMargin = right; lp.bottomMargin = bottom;
                view.setLayoutParams(lp);
            }
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        ImageLoader.init(getCacheDir(), view);   // async remote <img> cache + repaint
        extractAssets();                          // unpack APK assets → filesDir/assets/
        view.setAssetBase(getFilesDir().getAbsolutePath());  // relative <img src> base
        // "@demo" = built-in interactive demo; an asset name renders that page.
        view.setHtml(loadAsset("showcase.html"));
    }

    /** Open an external URL from the Tina4Pascal HTML link handler. */
    public static void openExternalUrl(final String url) {
        Log.i("Tina4Link", "openExternalUrl(" + url + ")");
        final MainActivity activity = instance;
        if (activity == null || url == null || url.isEmpty()) {
            Log.e("Tina4Link", "Cannot open URL: activity or URL is missing");
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivity(intent);
            } catch (Exception error) {
                Log.e("Tina4Link", "No Android handler for " + url, error);
            }
        });
    }

    /** Copy bundled APK assets (icons, images, fonts/) to filesDir/assets/ so the
     *  engine can load a relative {@code <img src="assets/…">} or a bundled
     *  {@code fonts/*.ttf} as a real file. HTML is loaded directly via loadAsset,
     *  so skip *.html. Recursive, so subfolders like fonts/ survive. */
    private void extractAssets() {
        try {
            File base = new File(getFilesDir(), "assets");
            base.mkdirs();
            extractAssetDir("", base);
        } catch (Exception e) { /* no assets to extract */ }
    }

    private void extractAssetDir(String rel, File dest) {
        try {
            String[] names = getAssets().list(rel);
            if (names == null) return;
            for (String n : names) {
                String child = rel.isEmpty() ? n : rel + "/" + n;
                String[] sub = getAssets().list(child);
                if (sub != null && sub.length > 0) {           // a directory → recurse
                    File d = new File(dest, n); d.mkdirs();
                    extractAssetDir(child, d);
                    continue;
                }
                if (n.endsWith(".html")) continue;
                try (InputStream in = getAssets().open(child);
                     FileOutputStream out = new FileOutputStream(new File(dest, n))) {
                    byte[] buf = new byte[8192]; int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                } catch (Exception e) { /* skip an unreadable asset */ }
            }
        } catch (Exception e) { /* skip */ }
    }

    /** Called from the view when an <input type=file> is tapped. */
    void pickFile(Tina4View from) {
        this.view = from;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(Intent.createChooser(intent, "Select a file"), REQ_PICK_FILE);
        } catch (Exception e) { /* no picker available */ }
    }

    /** Called from the view when a <camera> tag is tapped. */
    void captureCamera(Tina4View from) {
        this.view = from;
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQ_CAPTURE);   // thumbnail returns in the result
        } catch (Exception e) { /* no camera app */ }
    }

    /** <recorder> tapped idle: start MIC capture to an AAC .m4a (asking for the
     *  RECORD_AUDIO permission first if needed). On any failure the view rolls the
     *  control back to idle via onRecordingDone(""). */
    void startRecording(Tina4View from) {
        this.view = from;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission("android.permission.RECORD_AUDIO")
                   != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingRecord = true;
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQ_MIC);
            return;
        }
        beginRecording();
    }

    private void beginRecording() {
        try {
            recPath = new File(getFilesDir(),
                "tina4-rec-" + System.currentTimeMillis() + ".m4a").getAbsolutePath();
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recPath);
            recorder.prepare();
            recorder.start();
            Tina4CaptureService.start(this);   // E4: keep mic alive while backgrounded/locked
            startLevelPoll();                  // E1: feed the live VU meter
        } catch (Exception e) {
            if (recorder != null) { try { recorder.release(); } catch (Exception ignore) {} recorder = null; }
            recPath = null;
            if (view != null) view.onRecordingDone("");   // roll back to idle
        }
    }

    /** <recorder> tapped while armed: stop + hand the file back. */
    /** E1: while recording, poll MediaRecorder.getMaxAmplitude() ~16x/s and push a
     *  normalised 0..1 level into the engine so any [data-vu] element animates. */
    private void startLevelPoll() {
        stopLevelPoll();
        levelPoll = new Runnable() {
            public void run() {
                if (recorder == null) return;
                int amp = 0;
                try { amp = recorder.getMaxAmplitude(); } catch (Exception ignore) {}
                float lvl = Math.min(1f, (float) amp / 10000f);   // ~speech fills the bar
                if (view != null) view.pushAudioLevel(lvl);
                levelHandler.postDelayed(this, 60);
            }
        };
        levelHandler.postDelayed(levelPoll, 60);
    }

    private void stopLevelPoll() {
        if (levelPoll != null) { levelHandler.removeCallbacks(levelPoll); levelPoll = null; }
        if (view != null) view.pushAudioLevel(0f);   // settle the meter to zero
    }

    void stopRecording() {
        stopLevelPoll();                       // E1: stop the VU feed
        Tina4CaptureService.stop(this);        // E4: release the foreground slot
        String path = "";
        if (recorder != null) {
            try { recorder.stop(); path = recPath != null ? recPath : ""; }
            catch (Exception e) { path = ""; }
            try { recorder.release(); } catch (Exception ignore) {}
            recorder = null; recPath = null;
        }
        if (view != null) view.onRecordingDone(path);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Tina4Share.REQUEST_CODE) {
            if (view != null) view.onShareResult(resultCode == RESULT_OK ? 0 : 6, "", "");
            return;
        }
        if (view == null || resultCode != RESULT_OK) return;
        if (requestCode == REQ_PICK_FILE && data != null && data.getData() != null) {
            view.onFilePicked(displayName(data.getData()));
        } else if (requestCode == REQ_CAPTURE && data != null) {
            // ACTION_IMAGE_CAPTURE without EXTRA_OUTPUT returns a thumbnail
            // Bitmap; save it to a file the native image loader can decode.
            Object thumb = data.getExtras() != null ? data.getExtras().get("data") : null;
            if (thumb instanceof Bitmap) {
                String path = saveBitmap((Bitmap) thumb);
                if (path != null) view.onPhotoCaptured(path);
            }
        }
    }

    /** The user answered the CAMERA permission dialog raised by the scanner. On a
     *  grant, re-open the camera by hand — the scanner's surface is already live so
     *  no further layout pass would re-trigger it. */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && view != null
                && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            view.onCameraGranted();
        }
        if (requestCode == REQ_MIC && pendingRecord) {
            pendingRecord = false;
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
                beginRecording();
            else if (view != null)
                view.onRecordingDone("");   // denied → roll back to idle
        }
        if (requestCode == Tina4Location.REQUEST_CODE) {
            boolean allowed = grantResults.length > 0 &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Tina4Location.permissionResult(allowed);
        }
    }

    /** Persist a captured bitmap to the app's files dir; return its path. */
    private String saveBitmap(Bitmap bmp) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(getFilesDir(), "IMG_" + stamp + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return out.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    /** Resolve a content: Uri to its human-readable filename. */
    private String displayName(Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) { }
        return name != null ? name : "file";
    }

    private String loadAsset(String name) {
        try {
            InputStream is = getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            is.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "<body><h1>Could not load controls.html</h1></body>";
        }
    }
}
