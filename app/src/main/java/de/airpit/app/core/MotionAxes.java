package de.airpit.app.core;

/** Selects independent screen-relative gyro motions before filtering/inversion. */
public final class MotionAxes {
    public static final int TURN=0,SIDE_TILT=1,FORWARD_TILT=2;
    public final int horizontal,vertical;

    public MotionAxes(int horizontal,int vertical){
        this.horizontal=valid(horizontal)?horizontal:TURN;
        int y=valid(vertical)?vertical:FORWARD_TILT;
        this.vertical=y==this.horizontal?(this.horizontal==FORWARD_TILT?TURN:FORWARD_TILT):y;
    }
    private static boolean valid(int value){return value>=TURN&&value<=FORWARD_TILT;}
    public MotionAxes horizontal(int value){
        return new MotionAxes(value,value==vertical?horizontal:vertical);
    }
    public MotionAxes vertical(int value){
        return new MotionAxes(value==horizontal?vertical:horizontal,value);
    }
    public MotionAxes swapped(){return new MotionAxes(vertical,horizontal);}
    public double[] rates(double gx,double gy,double gz,double ux,double uy,double uz,int rotation){
        double norm=Math.sqrt(ux*ux+uy*uy+uz*uz);
        double turn=norm<0.2?-gz:-(gx*ux+gy*uy+gz*uz)/norm;
        double forward,side;
        switch(rotation){
            case 1:forward=-gy;side=-gx;break;
            case 2:forward=gx;side=-gy;break;
            case 3:forward=gy;side=gx;break;
            default:forward=-gx;side=gy;break;
        }
        double[] motions={turn,side,forward};
        return new double[]{motions[horizontal],motions[vertical]};
    }
}
