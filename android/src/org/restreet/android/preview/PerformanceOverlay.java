package org.restreet.android.preview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;

/** Lightweight on-device performance HUD. GPU utilization is shown only when readable. */
public final class PerformanceOverlay extends TextView implements Choreographer.FrameCallback {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long frameStart,frames;
    private long[] cpuPrevious;
    private long[] gpuPrevious;
    private boolean running;
    private int fps;
    private int cpu=-1,gpu=-1;
    private final Runnable sample=new Runnable(){
        @Override public void run(){
            if(!running)return;
            cpu=readCpu();
            gpu=readGpu();
            String gpuText=gpu<0?"n/d":gpu+"%";
            setText("FPS  "+fps+"\nCPU  "+(cpu<0?"n/d":cpu+"%")+"\nGPU  "+gpuText);
            handler.postDelayed(this,1000);
        }
    };
    public PerformanceOverlay(Context context){
        super(context);
        setTextColor(0xFFFFFFFF);setTextSize(12);setPadding(12,8,12,8);
        setBackgroundColor(0xCC101713);setContentDescription("Monitor de desempenho: FPS, CPU e GPU");
    }
    public void start(){
        if(running)return;
        running=true;frameStart=SystemClock.uptimeMillis();frames=0;
        cpuPrevious=readCpuCounters();gpuPrevious=readGpuCounters();
        Choreographer.getInstance().postFrameCallback(this);
        handler.post(sample);
    }
    public void stop(){
        running=false;handler.removeCallbacks(sample);
        Choreographer.getInstance().removeFrameCallback(this);
    }
    @Override public void doFrame(long frameTimeNanos){
        if(!running)return;
        frames++;
        long now=SystemClock.uptimeMillis();
        if(now-frameStart>=1000){fps=(int)Math.round(frames*1000.0/(now-frameStart));frames=0;frameStart=now;}
        Choreographer.getInstance().postFrameCallback(this);
    }
    private int readCpu(){
        long[] now=readCpuCounters();
        if(now==null)return -1;
        if(cpuPrevious==null){cpuPrevious=now;return -1;}
        long total=now[0]-cpuPrevious[0],idle=now[1]-cpuPrevious[1];
        cpuPrevious=now;
        if(total<=0)return -1;
        return (int)Math.max(0,Math.min(100,Math.round(100.0*(total-idle)/total)));
    }
    private long[] readCpuCounters(){
        try(BufferedReader r=new BufferedReader(new FileReader("/proc/stat"))){
            String line=r.readLine();
            if(line==null||!line.startsWith("cpu "))return null;
            String[] v=line.trim().split("\\s+");
            long total=0;for(int i=1;i<v.length;i++)total+=Long.parseLong(v[i]);
            long idle=Long.parseLong(v[4])+(v.length>5?Long.parseLong(v[5]):0);
            return new long[]{total,idle};
        }catch(Exception e){return null;}
    }
    private int readGpu(){
        long[] now=readGpuCounters();
        if(now==null)return -1;
        if(gpuPrevious==null){gpuPrevious=now;return -1;}
        long busy=now[0]-gpuPrevious[0],total=now[1]-gpuPrevious[1];gpuPrevious=now;
        if(total<=0||busy<0)return -1;
        return (int)Math.max(0,Math.min(100,Math.round(100.0*busy/total)));
    }
    private long[] readGpuCounters(){
        String[] paths={"/sys/class/kgsl/kgsl-3d0/gpubusy","/sys/class/kgsl/kgsl-3d0/gpu_busy"};
        for(String path:paths){
            try(BufferedReader r=new BufferedReader(new FileReader(path))){
                String[] v=r.readLine().trim().split("\\s+");
                if(v.length>=2)return new long[]{Long.parseLong(v[0]),Long.parseLong(v[1])};
                if(v.length==1)return new long[]{Long.parseLong(v[0]),100};
            }catch(Exception ignored){}
        }
        File root=new File("/sys/class/devfreq");
        File[] entries=root.listFiles();
        if(entries!=null)for(File entry:entries){
            String name=entry.getName().toLowerCase(java.util.Locale.ROOT);
            if(!name.contains("gpu"))continue;
            for(String leaf:new String[]{"gpu_busy_percent","load"}){
                try(BufferedReader r=new BufferedReader(new FileReader(new File(entry,leaf)))){
                    String value=r.readLine().trim().replace("%","");
                    int n=Integer.parseInt(value);if(n>=0&&n<=100)return new long[]{n,100};
                }catch(Exception ignored){}
            }
        }
        return null;
    }
}
