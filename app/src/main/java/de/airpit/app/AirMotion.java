package de.airpit.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import de.airpit.app.core.MotionFilter;
import de.airpit.app.core.MotionAxes;

/** Relative air pointing with selectable, rotation-aware gyro motions. */
public final class AirMotion {
    public interface Sink { void move(int x,int y);void calibrated(); }
    private final SensorManager manager;
    private final Sensor gyro,gravity;
    private final Sink sink;
    private final MotionFilter filter=new MotionFilter();
    private volatile MotionAxes axes=new MotionAxes(MotionAxes.TURN,MotionAxes.FORWARD_TILT);
    private final Handler input,main=new Handler(Looper.getMainLooper());
    private SensorEventListener listener;
    private volatile long epoch,startedAt;
    private long processedEpoch=-1;
    private final AtomicBoolean filterReset=new AtomicBoolean(),calibrationRequested=new AtomicBoolean();
    private long previous,calibrationStart;
    private final double[] bias=new double[3],sum=new double[3];
    private final double[] up={0,0,1};
    private int samples;
    private volatile boolean running,calibrating,holding;
    public volatile double gain=1350,smoothing=0.01975,deadzone=0.012;
    public volatile boolean invertX,invertY;
    public volatile int rotation;

    public AirMotion(Context context,Sink sink) {
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);this.sink=sink;
        gyro=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor preferred=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravity=preferred!=null?preferred:(manager==null?null:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        HandlerThread thread=new HandlerThread("AirPit gyro",android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);thread.start();input=new Handler(thread.getLooper());
    }
    public boolean available() {return gyro!=null;}
    public void setAxes(int horizontal,int vertical){
        MotionAxes selected=new MotionAxes(horizontal,vertical);
        if(selected.horizontal!=axes.horizontal||selected.vertical!=axes.vertical){axes=selected;filterReset.set(true);}
    }
    public boolean start() {
        if(gyro==null)return false;
        stop();final long token=++epoch;startedAt=SystemClock.elapsedRealtimeNanos();running=true;
        listener=new SensorEventListener(){public void onSensorChanged(SensorEvent e){consume(e,token);}public void onAccuracyChanged(Sensor s,int accuracy){}};
        running=manager.registerListener(listener,gyro,SensorManager.SENSOR_DELAY_GAME,0,input);
        if(running&&gravity!=null)manager.registerListener(listener,gravity,SensorManager.SENSOR_DELAY_GAME,0,input);
        return running;
    }
    public void stop() {running=false;epoch++;calibrating=false;calibrationRequested.set(false);filterReset.set(true);SensorEventListener old=listener;listener=null;if(manager!=null&&old!=null)manager.unregisterListener(old);}
    public void hold(boolean value) {holding=value;filterReset.set(true);}
    public void calibrate() {
        if(!running)return;
        calibrationRequested.set(true);
    }
    public boolean calibrating() {return running&&(calibrating||calibrationRequested.get());}
    private void consume(SensorEvent e,long token) {
        if(!running||token!=epoch)return;
        if(processedEpoch!=token){processedEpoch=token;previous=0;filter.reset();calibrating=false;}
        if(filterReset.getAndSet(false)){previous=0;filter.reset();}
        if(calibrationRequested.getAndSet(false)){calibrating=true;calibrationStart=0;samples=0;sum[0]=sum[1]=sum[2]=0;previous=0;filter.reset();}
        if(!MotionFilter.freshSample(e.timestamp,SystemClock.elapsedRealtimeNanos(),startedAt)){if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE){previous=0;filter.reset();}return;}
        if(e.sensor.getType()!=Sensor.TYPE_GYROSCOPE) {
            double norm=Math.sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]);
            if(norm>2)for(int i=0;i<3;i++)up[i]=0.90*up[i]+0.10*e.values[i]/norm;
            return;
        }
        if(calibrating) {
            double norm=Math.sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]);
            if(norm>0.10) {calibrationStart=0;samples=0;sum[0]=sum[1]=sum[2]=0;return;}
            if(calibrationStart==0)calibrationStart=e.timestamp;
            for(int i=0;i<3;i++)sum[i]+=e.values[i];samples++;
            if(e.timestamp-calibrationStart>=800_000_000L&&samples>=15) {
                for(int i=0;i<3;i++)bias[i]=sum[i]/samples;
                calibrating=false;previous=0;filter.reset();main.post(()->{if(running&&token==epoch)sink.calibrated();});
            }
            return;
        }
        long last=previous;previous=e.timestamp;
        if(holding||last==0)return;
        double[] rates=axes.rates(e.values[0]-bias[0],e.values[1]-bias[1],e.values[2]-bias[2],up[0],up[1],up[2],rotation);
        int[] move=filter.step(rates[0],rates[1],(e.timestamp-last)/1e9,gain,smoothing,deadzone,invertX,invertY);
        if(running&&token==epoch&&!holding&&(move[0]!=0||move[1]!=0))sink.move(move[0],move[1]);
    }
}
