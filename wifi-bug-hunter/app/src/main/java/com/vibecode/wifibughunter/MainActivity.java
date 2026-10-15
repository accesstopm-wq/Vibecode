package com.vibecode.wifibughunter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends android.app.Activity {
    private static final int REQ_PERMS = 42;
    private static final long SAMPLE_MS = 15000;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WifiManager wifi;
    private TextView status;
    private TextView logView;
    private volatile boolean running;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!running) return;
            worker.execute(() -> sample(false));
            handler.postDelayed(this, SAMPLE_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        buildUi();
        if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMS);
        log("=== WiFi Bug Hunter started ===");
        log("Log file: /storage/emulated/0/Documents/wifilogs.txt");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 16);
        status = new TextView(this); status.setTextSize(18); status.setText("STOPPED");
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(this);
        Button start = new Button(this); start.setText("START MONITOR");
        Button test = new Button(this); test.setText("TEST NOW");
        Button settings = new Button(this); settings.setText("WIFI SETTINGS");
        buttons.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(test, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(settings, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);
        logView = new TextView(this); logView.setTextSize(11); logView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(logView, new LinearLayout.LayoutParams(-1, 0, 1));
        start.setOnClickListener(v -> {
            running = !running;
            if (running) { status.setText("MONITORING — sample every 15s"); log("MONITOR ON"); handler.removeCallbacks(sampler); handler.post(sampler); }
            else { status.setText("STOPPED"); log("MONITOR OFF"); }
        });
        test.setOnClickListener(v -> worker.execute(() -> sample(true)));
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        setContentView(root);
    }

    private void sample(boolean activeTest) {
        WifiInfo wi = wifi.getConnectionInfo();
        StringBuilder s = new StringBuilder("\n[").append(timestamp()).append("] ");
        if (wi == null || wi.getNetworkId() == -1) { s.append("WIFI DISCONNECTED"); log(s.toString()); return; }
        int rssi = wi.getRssi(), freq = wi.getFrequency(), rx = wi.getRxLinkSpeedMbps(), tx = wi.getTxLinkSpeedMbps();
        s.append("WIFI connected\n")
         .append("  SSID: ").append(safeSsid(wi)).append("\n")
         .append("  BSSID: ").append(safeBssid(wi)).append("\n")
         .append("  RSSI: ").append(rssi).append(" dBm\n")
         .append("  Frequency: ").append(freq).append(" MHz\n")
         .append("  Standard: ").append(wifiStandard(wi)).append("\n")
         .append("  Link RX/TX: ").append(rx).append("/").append(tx).append(" Mbps\n");
        String gateway = gatewayAddress();
        if (gateway != null) s.append("  Gateway: ").append(gateway).append("\n");
        s.append("  Router ping: ").append(ping(gateway)).append(" ms\n")
         .append("  1.1.1.1 ping: ").append(ping("1.1.1.1")).append(" ms\n")
         .append("  DNS: ").append(dnsCheck()).append(" ms\n");
        if (activeTest) {
            double mbps = downloadMbps();
            s.append("  DOWNLOAD: ").append(String.format(Locale.US, "%.2f", mbps)).append(" Mbps");
            if (mbps >= 0 && mbps < 20 && rx >= 100) s.append("  <<< DEGRADED");
        }
        log(s.toString());
    }

    private double downloadMbps() {
        HttpURLConnection c = null;
        try {
            URL u = new URL("https://speed.cloudflare.com/__down?bytes=5000000");
            c = (HttpURLConnection) u.openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setUseCaches(false);
            long start = System.nanoTime(); int total = 0; byte[] buf = new byte[32768];
            try (java.io.InputStream in = c.getInputStream()) { int n; while ((n = in.read(buf)) != -1) total += n; }
            double sec = (System.nanoTime() - start) / 1_000_000_000.0;
            return sec > 0 ? total * 8.0 / sec / 1_000_000.0 : 0;
        } catch (Exception e) { log("  DOWNLOAD ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage()); return -1; }
        finally { if (c != null) c.disconnect(); }
    }

    private long ping(String host) {
        if (host == null) return -1;
        try {
            Process p = new ProcessBuilder("ping", "-c", "1", "-W", "2", host).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8); p.waitFor();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("time[=<]([0-9.]+) ms").matcher(out);
            return m.find() ? Math.round(Double.parseDouble(m.group(1))) : -1;
        } catch (Exception e) { return -1; }
    }

    private long dnsCheck() {
        try { long t = System.nanoTime(); InetAddress.getByName("cloudflare.com"); return Math.round((System.nanoTime() - t) / 1_000_000.0); }
        catch (Exception e) { return -1; }
    }

    private String gatewayAddress() {
        try { DhcpInfo d = wifi.getDhcpInfo(); if (d == null) return null; int ip = d.gateway;
            return String.format(Locale.US, "%d.%d.%d.%d", ip & 255, (ip >> 8) & 255, (ip >> 16) & 255, (ip >> 24) & 255);
        } catch (Exception e) { return null; }
    }

    private String wifiStandard(WifiInfo w) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            switch (w.getWifiStandard()) { case 4: return "802.11n"; case 5: return "802.11ac"; case 6: return "802.11ax"; case 7: return "802.11ad"; case 8: return "802.11be"; }
        }
        return "unknown";
    }
    private String safeSsid(WifiInfo w) { try { return String.valueOf(w.getSSID()); } catch (Exception e) { return "?"; } }
    private String safeBssid(WifiInfo w) { try { return String.valueOf(w.getBSSID()); } catch (Exception e) { return "?"; } }
    private String timestamp() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()); }

    private synchronized void log(String text) {
        String line = text + "\n";
        runOnUiThread(() -> { logView.append(line); if (logView.getLayout() != null) { int y = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight(); if (y > 0) logView.scrollTo(0, y); } });
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS); if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(new File(dir, "wifilogs.txt"), true)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
        } catch (IOException e) { runOnUiThread(() -> logView.append("\n[LOG WRITE ERROR] " + e + "\n")); }
    }

    @Override protected void onDestroy() { running = false; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy(); }
}
