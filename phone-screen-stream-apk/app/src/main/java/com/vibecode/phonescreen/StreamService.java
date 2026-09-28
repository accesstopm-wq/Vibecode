package com.vibecode.phonescreen;

import android.app.*;
import android.content.pm.ServiceInfo;
import android.content.*;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.*;
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
    static final int PORT=8787, W=1280, H=720, FPS=30, VBIT=4000000;
    MediaProjection projection; VirtualDisplay display; MediaCodec video; Surface surface;
    AudioRecord audio; volatile boolean running; Server server; ExecutorService pool=Executors.newCachedThreadPool();
    PowerManager.WakeLock wake;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel c=new NotificationChannel("stream","Screen Stream",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
    }

    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(running) return START_STICKY;
        try {
            Notification n=new Notification.Builder(this,"stream").setContentTitle("Phone Screen Stream")
                    .setContentText("Streaming screen to local Wi-Fi").setSmallIcon(android.R.drawable.ic_media_play).build();
            if(Build.VERSION.SDK_INT>=29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(1,n);

            int result=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
            Intent data=intent.getParcelableExtra("data");
            MediaProjectionManager pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=pm.getMediaProjection(result,data);
            if(projection==null) throw new Exception("MediaProjection unavailable");

            PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
            wake=p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PhoneScreenStream:keep");
            wake.acquire();

            setupVideo();
            setupAudio();
            running=true;
            server=new Server();
            pool.execute(server);
            pool.execute(this::videoLoop);
            pool.execute(this::audioLoop);
        } catch(Exception e) { e.printStackTrace(); stopSelf(); }
        return START_NOT_STICKY;
    }

    void setupVideo() throws Exception {
        video=MediaCodec.createEncoderByType("video/avc");
        MediaFormat f=MediaFormat.createVideoFormat("video/avc",W,H);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE,VBIT);
        f.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        try { f.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline); } catch(Exception ignored){}
        video.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface=video.createInputSurface();
        video.start();
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int dw=dm.widthPixels, dh=dm.heightPixels;
        display=projection.createVirtualDisplay("PhoneScreenStream",W,H,dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null);
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

    void videoLoop() {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        byte[] config=null;
        while(running) try {
            int ix=video.dequeueOutputBuffer(info,10000);
            if(ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f=video.getOutputFormat();
                byte[] a=getBytes(f,"csd-0"), b=getBytes(f,"csd-1");
                if(a!=null || b!=null) config=joinAnnex(a,b);
                if(config!=null) server.broadcast(3,0,config);
                continue;
            }
            if(ix<0) continue;
            if(info.size>0) {
                ByteBuffer bb=video.getOutputBuffer(ix);
                byte[] raw=new byte[info.size]; bb.position(info.offset); bb.get(raw);
                byte[] data=toAnnexB(raw);
                boolean key=(info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0;
                if(key && config!=null) data=concat(config,data);
                if(server!=null) server.broadcast(key?1:2,info.presentationTimeUs,data);
            }
            video.releaseOutputBuffer(ix,false);
        } catch(Exception e) { if(running) e.printStackTrace(); }
    }

    void audioLoop() {
        byte[] buf=new byte[38400];
        try {
            audio.startRecording();
            long start=System.nanoTime()/1000;
            while(running) {
                int n=audio.read(buf,0,buf.length);
                if(n>0) server.broadcast(4,(System.nanoTime()/1000)-start,Arrays.copyOf(buf,n));
            }
        } catch(Exception e) { if(running) e.printStackTrace(); }
    }

    static byte[] getBytes(MediaFormat f,String k){ try { ByteBuffer b=f.getByteBuffer(k); if(b==null)return null; byte[] x=new byte[b.remaining()]; b.get(x); return x;}catch(Exception e){return null;} }
    static byte[] concat(byte[] a,byte[] b){byte[] x=new byte[a.length+b.length];System.arraycopy(a,0,x,0,a.length);System.arraycopy(b,0,x,a.length,b.length);return x;}
    static byte[] joinAnnex(byte[] a,byte[] b){if(a==null)return b==null?null:toAnnexB(b); if(b==null)return toAnnexB(a); return concat(toAnnexB(a),toAnnexB(b));}
    static byte[] toAnnexB(byte[] x){
        if(x.length>=4 && x[0]==0 && x[1]==0 && ((x[2]==1)|| (x[2]==0&&x[3]==1))) return x;
        ByteArrayOutputStream o=new ByteArrayOutputStream(x.length+64); int p=0;
        while(p+4<=x.length){int n=((x[p]&255)<<24)|((x[p+1]&255)<<16)|((x[p+2]&255)<<8)|(x[p+3]&255);p+=4;if(n<=0||p+n>x.length){return x;}o.write(new byte[]{0,0,0,1},0,4);o.write(x,p,n);p+=n;}
        if(p<x.length)o.write(x,p,x.length-p); return o.toByteArray();
    }

    @Override public void onDestroy() {
        running=false;
        try{if(audio!=null){audio.stop();audio.release();}}catch(Exception ignored){}
        try{if(display!=null)display.release();}catch(Exception ignored){}
        try{if(surface!=null)surface.release();}catch(Exception ignored){}
        try{if(video!=null){video.stop();video.release();}}catch(Exception ignored){}
        try{if(projection!=null)projection.stop();}catch(Exception ignored){}
        try{if(server!=null)server.close();}catch(Exception ignored){}
        try{if(wake!=null&&wake.isHeld())wake.release();}catch(Exception ignored){}
        pool.shutdownNow(); super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}

    class Server implements Runnable {
        ServerSocket ss; final Set<Socket> clients=ConcurrentHashMap.newKeySet();
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
                    clients.add(s); try{while(r.readLine()!=null){} }finally{clients.remove(s);s.close();}
                } else {
                    String html=page(); byte[] body=html.getBytes("UTF-8");
                    OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("ISO-8859-1"));out.write(body);out.flush();s.close();
                }
            }catch(Exception e){try{s.close();}catch(Exception ignored){}}
        }
        synchronized void broadcast(int type,long ts,byte[] data){
            byte[] p=new byte[1+8+4+data.length];p[0]=(byte)type;ByteBuffer.wrap(p,1,8).putLong(ts);ByteBuffer.wrap(p,9,4).putInt(data.length);System.arraycopy(data,0,p,13,data.length);
            byte[] ws=frame(p);
            for(Socket s:clients)try{s.getOutputStream().write(ws);s.getOutputStream().flush();}catch(Exception e){clients.remove(s);try{s.close();}catch(Exception ignored){}}
        }
        byte[] frame(byte[] p){ByteArrayOutputStream o=new ByteArrayOutputStream(p.length+16);o.write(0x82);int n=p.length;if(n<126)o.write(n);else if(n<=65535){o.write(126);o.write((n>>>8)&255);o.write(n&255);}else{o.write(127);for(int i=7;i>=0;i--)o.write((n>>>(8*i))&255);}try{o.write(p);}catch(Exception ignored){}return o.toByteArray();}
        String page(){return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><style>html,body{margin:0;background:#000;color:#fff;height:100%;overflow:hidden}#v{width:100%;height:100%;object-fit:contain}#s{position:fixed;top:0;left:0;background:#000c;padding:8px;font:14px sans-serif}</style><canvas id=v></canvas><div id=s>Connecting…</div><script>"+
                "const c=document.querySelector('#v'),x=c.getContext('2d'),s=document.querySelector('#s');let vd,ac,queue=[],last=0;"+
                "function u8(b){return new DataView(b.buffer,b.byteOffset,b.byteLength)}"+
                "function start(){let w=new WebSocket('ws://'+location.host+'/ws');w.binaryType='arraybuffer';w.onopen=()=>s.textContent='Connected';w.onclose=()=>s.textContent='Disconnected';w.onmessage=e=>{let b=new Uint8Array(e.data),d=new DataView(e.data),t=b[0],ts=Number(d.getBigInt64(1)),n=d.getUint32(9),p=b.slice(13,13+n);if(t===3){let cfg=new TextDecoder().decode(p);init(cfg)}else if(t===1||t===2){if(vd)vd.decode(new EncodedVideoChunk({type:t===1?'key':'delta',timestamp:ts,data:p}))}else if(t===4)audio(p,ts)};}"+
                "function init(z){vd=new VideoDecoder({output:f=>{if(c.width!==f.displayWidth)c.width=f.displayWidth,c.height=f.displayHeight;x.drawImage(f,0,0);f.close()},error:e=>s.textContent='Video decoder: '+e});vd.configure({codec:'avc1.42E01E',optimizeForLatency:true,hardwareAcceleration:'prefer-hardware'});ac=new AudioContext();}"+
                "function audio(p,ts){if(!ac)return;let a=new Int16Array(p.buffer,p.byteOffset,p.byteLength/2),buf=ac.createBuffer(2,a.length/2,48000);for(let ch=0;ch<2;ch++){let q=buf.getChannelData(ch);for(let i=0;i<q.length;i++)q[i]=a[i*2+ch]/32768;}let z=ac.createBufferSource();z.buffer=buf;z.connect(ac.destination);let t=Math.max(ac.currentTime,last+.01);z.start(t);last=t+buf.duration;}"+
                "start();c.addEventListener('click',()=>ac&&ac.resume());</script>";}
        String ip(){try{Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();while(es.hasMoreElements()){NetworkInterface ni=es.nextElement();for(InterfaceAddress ia:ni.getInterfaceAddresses()){InetAddress a=ia.getAddress();if(a instanceof Inet4Address&&!a.isLoopbackAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "PHONE_IP";}
        void close(){try{if(ss!=null)ss.close();}catch(Exception ignored){}for(Socket s:clients)try{s.close();}catch(Exception ignored){}clients.clear();}
    }
}
