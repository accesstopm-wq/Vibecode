package com.vibecode.phonescreen;

import android.app.*;
import android.content.pm.ServiceInfo;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.Surface;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class StreamService extends Service {
    static final int PORT=8787, W=1280, H=720, FPS=20;
    MediaProjection projection; MediaProjection.Callback projectionCallback; VirtualDisplay display; ImageReader imageReader; Surface surface;
    AudioRecord audio; volatile boolean running; Server server; ExecutorService pool=Executors.newCachedThreadPool();
    PowerManager.WakeLock wake;

    void status(String s) { getSharedPreferences("stream",0).edit().putString("status",s).apply(); }

    @Override public void onCreate() {
        super.onCreate();
        status("Service created");
        NotificationChannel c=new NotificationChannel("stream","Screen Stream",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
    }

    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(running) return START_STICKY;
        try {
            status("Starting foreground service...");
            Notification n=new Notification.Builder(this,"stream").setContentTitle("Phone Screen Stream")
                    .setContentText("Streaming screen to local Wi-Fi").setSmallIcon(android.R.drawable.ic_media_play).build();
            if(Build.VERSION.SDK_INT>=29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(1,n);

            status("Getting screen capture...");
            int result=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
            Intent data=intent.getParcelableExtra("data");
            MediaProjectionManager pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=pm.getMediaProjection(result,data);
            if(projection==null) throw new Exception("MediaProjection unavailable");
            projectionCallback = new MediaProjection.Callback() {
                @Override public void onStop() {
                    status("Screen capture stopped by Android.");
                    stopSelf();
                }
            };
            projection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));

            status("Setting up video encoder...");
            PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
            wake=p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PhoneScreenStream:keep");
            wake.acquire();

            setupVideo();
            status("Setting up system audio...");
            setupAudio();
            running=true;
            status("Starting local server...");
            server=new Server();
            pool.execute(server);
            pool.execute(this::videoLoop);
            pool.execute(this::audioLoop);
            status("STREAM READY: http://"+server.ip()+":"+PORT+"/");
        } catch(Throwable e) {
            String msg="ERROR: "+e.getClass().getName()+": "+String.valueOf(e.getMessage());
            status(msg);
            try {
                Notification n=new Notification.Builder(this,"stream").setContentTitle("Phone Screen Stream - ERROR")
                        .setContentText(msg.length()>120?msg.substring(0,120):msg).setSmallIcon(android.R.drawable.ic_dialog_alert).build();
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2,n);
            } catch(Throwable ignored) {}
            e.printStackTrace();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    void setupVideo() throws Exception {
        imageReader=ImageReader.newInstance(W,H,android.graphics.PixelFormat.RGBA_8888,3);
        surface=imageReader.getSurface();
        DisplayMetrics dm=getResources().getDisplayMetrics();
        display=projection.createVirtualDisplay("PhoneScreenStream",W,H,dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null);
    }

    void videoLoop() {
        BitmapHolder holder=new BitmapHolder(W,H);
        long next=System.nanoTime();
        while(running) try {
            Image im=imageReader.acquireLatestImage();
            if(im!=null) {
                byte[] jpg=holder.toJpeg(im,70);
                im.close();
                if(jpg!=null && server!=null) server.broadcastJpeg(jpg);
            }
            next+=50_000_000L;
            long wait=next-System.nanoTime();
            if(wait>0) Thread.sleep(Math.min(50,wait/1_000_000L));
            else next=System.nanoTime();
        } catch(Exception e) { if(running) e.printStackTrace(); }
    }

    static class BitmapHolder {
        final int w,h;
        final Bitmap bitmap;
        byte[] row;
        BitmapHolder(int w,int h){this.w=w;this.h=h;bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}
        byte[] toJpeg(Image im,int quality){
            Image.Plane p=im.getPlanes()[0];
            int rs=p.getRowStride(), ps=p.getPixelStride();
            ByteBuffer bb=p.getBuffer();
            int needed=rs*h;
            if(row==null || row.length<needed) row=new byte[needed];
            bb.get(row,0,Math.min(bb.remaining(),needed));
            if(ps==4 && rs==w*4) {
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(row,0,w*h*4));
            } else {
                ByteBuffer src=ByteBuffer.wrap(row);
                int[] px=new int[w*h];
                for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                    int off=y*rs+x*ps;
                    int r=row[off]&255,g=row[off+1]&255,b=row[off+2]&255,a=(ps>=4?row[off+3]&255:255);
                    px[y*w+x]=(a<<24)|(r<<16)|(g<<8)|b;
                }
                bitmap.setPixels(px,0,w,0,0,w,h);
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream(w*h/6);
            bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out);
            return out.toByteArray();
        }
    }
    void setupAudio() throws Exception {
        int sr=48000, ch=AudioFormat.CHANNEL_IN_STEREO;
        AudioFormat fmt=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sr).setChannelMask(ch).build();
        AudioPlaybackCaptureConfiguration cfg=new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
        int min=AudioRecord.getMinBufferSize(sr,ch,AudioFormat.ENCODING_PCM_16BIT);
        audio=new AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(Math.max(min*2,38400))
                .setAudioPlaybackCaptureConfig(cfg).build();
    }

    void audioLoop() {
        byte[] buf=new byte[38400];
        try {
            audio.startRecording();
            long start=System.nanoTime()/1000;
            while(running) {
                int n=audio.read(buf,0,buf.length);
                if(n>0 && server!=null) server.broadcast(4,(System.nanoTime()/1000)-start,Arrays.copyOf(buf,n));
            }
        } catch(Exception e) { if(running) e.printStackTrace(); }
    }

    @Override public void onDestroy() {
        running=false;
        try{if(audio!=null){audio.stop();audio.release();}}catch(Exception ignored){}
        try{if(display!=null)display.release();}catch(Exception ignored){}
        try{if(surface!=null)surface.release();}catch(Exception ignored){}
        try{if(imageReader!=null)imageReader.close();}catch(Exception ignored){}
        try{if(projection!=null && projectionCallback!=null)projection.unregisterCallback(projectionCallback);}catch(Exception ignored){}
        try{if(projection!=null)projection.stop();}catch(Exception ignored){}
        try{if(server!=null)server.close();}catch(Exception ignored){}
        try{if(wake!=null&&wake.isHeld())wake.release();}catch(Exception ignored){}
        pool.shutdownNow(); super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}

    class Server implements Runnable {
        ServerSocket ss; final Set<Socket> clients=ConcurrentHashMap.newKeySet(); final Set<Socket> mjpegClients=ConcurrentHashMap.newKeySet();
        public void run(){
            try{
                ss=new ServerSocket(PORT,20,InetAddress.getByName("0.0.0.0"));
                System.out.println("STREAM http://"+ip()+":"+PORT+"/");
                while(running){final Socket s=ss.accept();pool.execute(()->handle(s));}
            }catch(Exception e){if(running)e.printStackTrace();}
        }
        void handle(Socket s){
            try{
                s.setTcpNoDelay(true); BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()));
                String line, key=null, path="/"; line=r.readLine(); if(line!=null){String[] q=line.split(" ");if(q.length>1)path=q[1];}
                while((line=r.readLine())!=null&&!line.isEmpty()) if(line.toLowerCase().startsWith("sec-websocket-key:")) key=line.substring(line.indexOf(':')+1).trim();
                if("/ws".equals(path)&&key!=null){
                    String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("ISO-8859-1")));
                    OutputStream out=s.getOutputStream(); out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes("ISO-8859-1"));out.flush();
                    clients.add(s);
                    try{while(r.readLine()!=null){} }finally{clients.remove(s);s.close();}
                } else if("/mjpeg".equals(path)) {
                    OutputStream out=s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-cache, no-store, must-revalidate\r\nPragma: no-cache\r\nConnection: close\r\n\r\n").getBytes("ISO-8859-1")); out.flush();
                    mjpegClients.add(s);
                    try{while(r.readLine()!=null){} }finally{mjpegClients.remove(s);s.close();}
                } else {
                    String html=page(); byte[] body=html.getBytes("UTF-8");
                    OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("ISO-8859-1"));out.write(body);out.flush();s.close();
                }
            }catch(Exception e){try{s.close();}catch(Exception ignored){}}
        }
        synchronized void broadcastJpeg(byte[] jpg){
            byte[] head=("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "+jpg.length+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            byte[] tail="\r\n".getBytes("ISO-8859-1");
            for(Socket s:mjpegClients)try{OutputStream o=s.getOutputStream();o.write(head);o.write(jpg);o.write(tail);o.flush();}catch(Exception e){mjpegClients.remove(s);try{s.close();}catch(Exception ignored){}}
        }
        synchronized void broadcast(int type,long ts,byte[] data){
            byte[] ws=framePacket(type,ts,data);
            for(Socket s:clients)try{s.getOutputStream().write(ws);s.getOutputStream().flush();}catch(Exception e){clients.remove(s);try{s.close();}catch(Exception ignored){}}
        }
        byte[] framePacket(int type,long ts,byte[] data){
            byte[] p=new byte[1+8+4+data.length];p[0]=(byte)type;ByteBuffer.wrap(p,1,8).putLong(ts);ByteBuffer.wrap(p,9,4).putInt(data.length);System.arraycopy(data,0,p,13,data.length);
            return frame(p);
        }
        byte[] frame(byte[] p){ByteArrayOutputStream o=new ByteArrayOutputStream(p.length+16);o.write(0x82);int n=p.length;if(n<126)o.write(n);else if(n<=65535){o.write(126);o.write((n>>>8)&255);o.write(n&255);}else{o.write(127);for(int i=7;i>=0;i--)o.write((n>>>(8*i))&255);}try{o.write(p);}catch(Exception ignored){}return o.toByteArray();}
        String page(){return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><style>html,body{margin:0;background:#000;color:#fff;height:100%;overflow:hidden}#v{width:100%;height:100%;object-fit:contain}#s{position:fixed;top:0;left:0;background:#000c;padding:8px;font:14px sans-serif}</style><img id=v src='/mjpeg'><div id=s>Connecting…</div><script>"+
                "const s=document.querySelector('#s'),v=document.querySelector('#v');let ac,last=0,audioPackets=0;"+
                "function start(){let w=new WebSocket('ws://'+location.host+'/ws');w.binaryType='arraybuffer';w.onopen=()=>s.textContent='Video connected';w.onclose=()=>s.textContent='Audio disconnected';w.onmessage=e=>{let b=new Uint8Array(e.data),d=new DataView(e.data),t=b[0],ts=Number(d.getBigInt64(1)),n=d.getUint32(9),p=b.slice(13,13+n);if(t===4)audio(p,ts)};}"+
                "function audio(p,ts){audioPackets++;try{if(!ac)ac=new AudioContext();let a=new Int16Array(p.buffer,p.byteOffset,p.byteLength/2),buf=ac.createBuffer(2,a.length/2,48000);for(let ch=0;ch<2;ch++){let q=buf.getChannelData(ch);for(let i=0;i<q.length;i++)q[i]=a[i*2+ch]/32768;}let z=ac.createBufferSource();z.buffer=buf;z.connect(ac.destination);let t=Math.max(ac.currentTime,last+.01);z.start(t);last=t+buf.duration;s.textContent='VIDEO OK | audio packets: '+audioPackets;}catch(e){s.textContent='AUDIO ERROR: '+e.message;}}"+
                "v.onload=()=>s.textContent='VIDEO OK';v.onerror=()=>s.textContent='VIDEO ERROR';start();document.body.addEventListener('click',()=>{if(ac)ac.resume();});</script>";}


        String ip(){try{Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();while(es.hasMoreElements()){NetworkInterface ni=es.nextElement();for(InterfaceAddress ia:ni.getInterfaceAddresses()){InetAddress a=ia.getAddress();if(a instanceof Inet4Address&&!a.isLoopbackAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "PHONE_IP";}
        void close(){try{if(ss!=null)ss.close();}catch(Exception ignored){}for(Socket s:clients)try{s.close();}catch(Exception ignored){}for(Socket s:mjpegClients)try{s.close();}catch(Exception ignored){}clients.clear();mjpegClients.clear();}
    }
}
