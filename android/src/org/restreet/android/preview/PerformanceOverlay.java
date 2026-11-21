package org.restreet.android.preview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;

/** Runtime HUD: game frame ticks, process CPU and readable GPU counters. */
public final class PerformanceOverlay extends TextView {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean running;
    private RandomAccessFile log;
    private long lastFrameCount=-1,lastFrameTime;
    private long lastCpuTime=-1,lastWallTime;
    private long lastGpuBusy=-1,lastGpuTotal=-1;
    private int fps=-1,cpu=-1,gpu=-1;

    private final Runnable sample=new Runnable(){
        @Override public void run(){
            if(!running)return;
            fps=readGameFps();
            cpu=readCpu();
            gpu=readGpu();
            setText("FPS jogo  "+(fps<0?"aguardando":fps+"")+
                "\nCPU app  "+(cpu<0?"n/d":cpu+"%")+
                "\nGPU  "+(gpu<0?"indisponível":gpu+"%"));
            handler.postDelayed(this,1000);
        }
    };

    public PerformanceOverlay(Context context){
        super(context);
        setTextColor(0xFFFFFFFF);setTextSize(12);setPadding(12,8,12,8);
        setBackgroundColor(0xCC101713);
        setContentDescription("Métricas reais do jogo: FPS, CPU do aplicativo e GPU quando o driver permite leitura");
    }

    public void start(){
        if(running)return;
        running=true;
        lastCpuTime=Process.getElapsedCpuTime();
        lastWallTime=SystemClock.elapsedRealtime();
        lastGpuBusy=-1;lastGpuTotal=-1;lastFrameCount=-1;fps=-1;
        openGameLog();
        handler.post(sample);
    }

    public void stop(){
        running=false;handler.removeCallbacks(sample);
        closeGameLog();
    }

    private void openGameLog(){
        closeGameLog();
        try{
            File file=new File(getContext().getFilesDir(),"restreet.log");
            if(file.isFile()){log=new RandomAccessFile(file,"r");log.seek(log.length());}
        }catch(Exception ignored){log=null;}
    }

    private void closeGameLog(){
        try{if(log!=null)log.close();}catch(Exception ignored){}
        log=null;
    }

    private int readGameFps(){
        try{
            if(log==null){openGameLog();if(log==null)return -1;}
            File file=new File(getContext().getFilesDir(),"restreet.log");
            if(file.length()<log.getFilePointer())log.seek(0);
            String line;
            while((line=log.readLine())!=null){
                int marker=line.indexOf("GAME_FRAME_COUNT ");
                if(marker<0)continue;
                String value=line.substring(marker+17).trim().split("\\s+")[0];
                long count=Long.parseLong(value);
                long now=SystemClock.elapsedRealtime();
                if(lastFrameCount>=0&&count>=lastFrameCount&&now>lastFrameTime){
                    fps=(int)Math.max(0,Math.min(300,Math.round((count-lastFrameCount)*1000.0/(now-lastFrameTime))));
                }
                lastFrameCount=count;lastFrameTime=now;
            }
            return fps;
        }catch(Exception e){return -1;}
    }

    private int readCpu(){
        long cpuNow=Process.getElapsedCpuTime();
        long wallNow=SystemClock.elapsedRealtime();
        if(lastCpuTime<0||lastWallTime<0||cpuNow<lastCpuTime||wallNow<=lastWallTime){
            lastCpuTime=cpuNow;lastWallTime=wallNow;return -1;
        }
        long cpuDelta=cpuNow-lastCpuTime,wallDelta=wallNow-lastWallTime;
        lastCpuTime=cpuNow;lastWallTime=wallNow;
        double usage=cpuDelta*100.0/(wallDelta*Math.max(1,Runtime.getRuntime().availableProcessors()));
        return (int)Math.max(0,Math.min(100,Math.round(usage)));
    }

    private int readGpu(){
        long[] counters=readKgslBusy();
        if(counters!=null){
            if(lastGpuBusy<0||lastGpuTotal<0){lastGpuBusy=counters[0];lastGpuTotal=counters[1];return -1;}
            long busy=counters[0]-lastGpuBusy,total=counters[1]-lastGpuTotal;
            lastGpuBusy=counters[0];lastGpuTotal=counters[1];
            if(total>0&&busy>=0)return (int)Math.max(0,Math.min(100,Math.round(100.0*busy/total)));
        }
        return readDirectGpuLoad();
    }

    private long[] readKgslBusy(){
        String[] paths={"/sys/class/kgsl/kgsl-3d0/gpubusy","/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpubusy"};
        for(String path:paths){
            try(BufferedReader r=new BufferedReader(new FileReader(path))){
                String[] v=r.readLine().trim().split("\\s+");
                if(v.length>=2)return new long[]{Long.parseLong(v[0]),Long.parseLong(v[1])};
            }catch(Exception ignored){}
        }
        return null;
    }

    private int readDirectGpuLoad(){
        String[] paths={"/sys/class/kgsl/kgsl-3d0/gpu_busy_percent",
            "/sys/class/devfreq/kgsl-3d0/gpu_busy_percent"};
        for(String path:paths){
            try(BufferedReader r=new BufferedReader(new FileReader(path)){
                String value=r.readLine().trim().replace("%","");
                if(value.matches("[0-9]{1,3}")){
                    int n=Integer.parseInt(value);if(n>=0&&n<=100)return n;
                }
            }catch(Exception ignored){}
        }
        File root=new File("/sys/class/devfreq");
        File[] entries=root.listFiles();
        if(entries!=null)for(File entry:entries){
            if(!entry.getName().toLowerCase(java.util.Locale.ROOT).contains("gpu"))continue;
            try(BufferedReader r=new BufferedReader(new FileReader(new File(entry,"gpu_busy_percent")))){
                String value=r.readLine().trim().replace("%","");
                if(value.matches("[0-9]{1,3}")){
                    int n=Integer.parseInt(value);if(n>=0&&n<=100)return n;
                }
            }catch(Exception ignored){}
        }
        return -1;
    }
}
