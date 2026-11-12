package com.costoluce.importatore;

/** Dimensions in dp: detail is selected from BOTH axes, never by shrinking text. */
final class WidgetLayoutSpec {
    static final int MIN_WIDTH=96, MIN_HEIGHT=48, DEFAULT_WIDTH=130, DEFAULT_HEIGHT=64;
    final int width,height,level,padding;
    WidgetLayoutSpec(int width,int height){
        this.width=Math.max(64,Math.min(1600,width));
        this.height=Math.max(32,Math.min(1600,height));
        level=this.width>=380&&this.height>=340?2:this.width>=320&&this.height>=290?1:0;
        padding=level==2?16:level==1?14:12;
    }
    boolean compact(){return level==0;}
    boolean detailed(){return level==2;}
    boolean statsOnly(){return height<110;}
    boolean smallSummary(){return !statsOnly()&&(width<280||height<260);}
    boolean fullPages(){return !statsOnly()&&!smallSummary();}
    boolean wideStats(){return statsOnly()&&width>=260;}
    boolean smallDate(){return smallSummary()&&height>=150;}
    boolean smallRefresh(){return smallSummary()&&width>=200&&height>=160;}
    int contentWidth(){return width-2*padding;}
    int chartHeight(float fontScale,boolean statusVisible){
        int line=(int)Math.ceil(15*Math.max(1,fontScale));
        return Math.max(40,height-2*padding-48-48-4-line-(statusVisible?line:0));
    }
}
