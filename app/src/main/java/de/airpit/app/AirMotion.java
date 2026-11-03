package de.airpit.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import de.airpit.app.core.MotionFilter;

/** Relative air pointing using yaw projected onto gravity and phone X pitch. */
public final class AirMotion implements SensorEventListener {
    public interface Sink { void move(int x,int y);void calibrated(); }
    private final SensorManager manager;
    private final Sensor gyro,gravity;
    private final Sink sink;
    private final MotionFilter filter=new MotionFilter();
    private long previous,calibrationStart;
    private final double[] bias=new double[3],sum=new double[3];
    private final double[] up={0,0,1};
    private int samples;
    private boolean running,calibrating,holding;
    public double gain=1350,smoothing=0.035,deadzone=0.012;
    public boolean invertX,invertY;

    public AirMotion(Context context,Sink sink) {
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);this.sink=sink;
        gyro=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor preferred=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravity=preferred!=null?preferred:(manager==null?null:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
    }
    public boolean available() {return gyro!=null;}
    public boolean start() {
        if(gyro==null)return false;
        stop();running=manager.registerListener(this,gyro,SensorManager.SENSOR_DELAY_GAME);
        if(running&&gravity!=null)manager.registerListener(this,gravity,SensorManager.SENSOR_DELAY_GAME);
        return running;
    }
    public void stop() {if(manager!=null)manager.unregisterListener(this);running=calibrating=false;previous=0;filter.reset();}
    public void hold(boolean value) {holding=value;previous=0;filter.reset();}
    public void calibrate() {
        if(!running)return;
        calibrating=true;calibrationStart=0;samples=0;sum[0]=sum[1]=sum[2]=0;previous=0;filter.reset();
    }
    public boolean calibrating() {return calibrating;}
    @Override public void onSensorChanged(SensorEvent e) {
        if(!running)return;
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
                calibrating=false;previous=0;filter.reset();sink.calibrated();
            }
            return;
        }
        long last=previous;previous=e.timestamp;
        if(holding||last==0)return;
        double norm=Math.sqrt(up[0]*up[0]+up[1]*up[1]+up[2]*up[2]);
        double yaw=norm<0.2?-(e.values[2]-bias[2]):
            -((e.values[0]-bias[0])*up[0]+(e.values[1]-bias[1])*up[1]+(e.values[2]-bias[2])*up[2])/norm;
        int[] move=filter.step(yaw,-(e.values[0]-bias[0]),(e.timestamp-last)/1e9,gain,smoothing,deadzone,invertX,invertY);
        if(move[0]!=0||move[1]!=0)sink.move(move[0],move[1]);
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
}
