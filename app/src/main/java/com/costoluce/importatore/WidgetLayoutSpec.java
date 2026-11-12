package com.costoluce.importatore;

/** Dimensions in dp: detail is selected from BOTH axes, never by shrinking text. */
final class WidgetLayoutSpec {
    static final int MIN_WIDTH=280, MIN_HEIGHT=260;
    final int width,height,level,padding;
    WidgetLayoutSpec(int width,int height){
        this.width=Math.max(160,Math.min(1600,width));
        this.height=Math.max(180,Math.min(1600,height));
        level=this.width>=380&&this.height>=340?2:this.width>=320&&this.height>=290?1:0;
        padding=level==2?16:level==1?14:12;
    }
    boolean compact(){return level==0;}
    boolean detailed(){return level==2;}
    int contentWidth(){return width-2*padding;}
    int chartHeight(float fontScale,boolean statusVisible){
        int line=(int)Math.ceil(15*Math.max(1,fontScale));
        return Math.max(40,height-2*padding-48-48-4-line-(statusVisible?line:0));
    }
}
