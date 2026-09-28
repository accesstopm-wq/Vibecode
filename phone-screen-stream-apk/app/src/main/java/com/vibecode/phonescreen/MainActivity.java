package com.vibecode.phonescreen;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.graphics.Color;

public class MainActivity extends Activity {
    static final int REQ_CAPTURE = 100;
    static final int REQ_AUDIO = 101;
    MediaProjectionManager projectionManager;
    TextView status;
    final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        projectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40,40,40,40);

        TextView title = new TextView(this);
        title.setText("Phone Screen Stream");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        box.addView(title);

        status = new TextView(this);
        status.setText("\n1. Press Start\n2. Allow screen + audio capture\n3. Open the shown address on the TV");
        status.setTextSize(17);
        box.addView(status);

        Button start = new Button(this);
        start.setText("START STREAM");
        box.addView(start);

        Button stop = new Button(this);
        stop.setText("STOP");
        box.addView(stop);

        start.setOnClickListener(v -> requestPermissionsAndCapture());
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, StreamService.class));
            status.setText("Stopped.");
        });

        setContentView(box);
        handler.postDelayed(new Runnable(){ public void run(){ updateStatus(); handler.postDelayed(this,500); }},500);
    }

    void updateStatus() { String s=getSharedPreferences("stream",0).getString("status",""); if(!s.isEmpty() && !s.equals("Service created")) status.setText(s); }

    void requestPermissionsAndCapture() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        launchCapture();
    }

    void launchCapture() {
        status.setText("Waiting for Android screen-capture permission...");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode != RESULT_OK || data == null) {
                status.setText("Screen capture permission denied.");
                return;
            }
            Intent i = new Intent(this, StreamService.class);
            i.putExtra("resultCode", resultCode);
            i.putExtra("data", data);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("Starting stream...");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] p, int[] r) {
        super.onRequestPermissionsResult(requestCode,p,r);
        if (requestCode == REQ_AUDIO) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) launchCapture();
            else status.setText("Microphone/audio permission is required for system-audio capture.");
        }
    }
}
