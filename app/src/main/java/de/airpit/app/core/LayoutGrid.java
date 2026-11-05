package de.airpit.app.core;

/** Watt'n'Pit's half-column/half-row editing grid, expressed in native dp. */
public final class LayoutGrid {
    public static final int COLUMNS=24;
    public final int width,row;
    public LayoutGrid(int width){this.width=Math.max(80,width);row=width>=620?15:13;}
    public int x(int column){return (int)Math.round((double)column*width/COLUMNS);}
    public int snapX(int value,int maximum){
        int column=(int)Math.round(Math.max(0L,value)*(double)COLUMNS/width);
        column=Math.min(COLUMNS,column);while(column>0&&x(column)>maximum)column--;
        return x(column);
    }
    public int snapY(int value,int maximum){long snapped=((Math.max(0L,value)+row/2)/row)*row;return (int)Math.min(snapped,Math.max(0,maximum/row*row));}
    public int below(int value){return (int)Math.min(Integer.MAX_VALUE,((Math.max(0L,value)+row-1)/row)*row);}
    public boolean alignedX(int value){for(int column=0;column<=COLUMNS;column++)if(x(column)==value)return true;return false;}
}
