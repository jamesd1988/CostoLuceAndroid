package com.costoluce.importatore;

/** Content budgets in dp; launcher cell counts are deliberately not assumed. */
final class WidgetLayoutSpec {
    static final int MIN_WIDTH=96, MIN_HEIGHT=48, DEFAULT_WIDTH=130, DEFAULT_HEIGHT=64;
    final int width,height,level,padding;
    final float fontScale;
    WidgetLayoutSpec(int width,int height){this(width,height,1f);}
    WidgetLayoutSpec(int width,int height,float fontScale){
        this.width=Math.max(64,Math.min(1600,width));
        this.height=Math.max(32,Math.min(1600,height));
        this.fontScale=Math.max(1,Math.min(3,fontScale));
        level=this.width>=380&&this.height>=340*this.fontScale?2:this.width>=320&&this.height>=290*this.fontScale?1:0;
        padding=this.width<180?10:12;
    }
    boolean compact(){return level==0;}
    boolean detailed(){return level==2;}
    boolean statsOnly(){return height<130*fontScale;}
    boolean fullPages(){return !statsOnly()&&((width>=130&&height>=330*fontScale)||(width>=240&&height>=230*fontScale)||(width>=340&&height>=190*fontScale));}
    boolean smallSummary(){return !statsOnly()&&!fullPages();}
    boolean unified(){return width>=360&&height>=420*fontScale;}
    boolean horizontalChart(){return width<240;}
    boolean sideBySide(){return width>=240;}
    boolean breakdown(){return !unified()&&height>=400*fontScale;}
    boolean shortWide(){return fullPages()&&height<250*fontScale;}
    boolean wideStats(){return statsOnly()&&width>=260;}
    boolean statsRefreshPossible(){return statsOnly()&&width>=120&&height>=56;}
    boolean smallDate(){return smallSummary()&&height>=170*fontScale;}
    boolean smallRefresh(){return smallSummary()&&width>=120;}
    int contentWidth(){return width-2*padding;}
    int chartHeight(float scale,boolean statusVisible){
        int available=height-2*padding-48-(unified()?summaryHeight()+8:52)-(int)Math.ceil(16*Math.max(1,scale))-(statusVisible?(int)Math.ceil(15*Math.max(1,scale)):0);
        // Do not turn a tall widget into four stretched columns.
        int cap=horizontalChart()?Math.round(4*64*fontScale):Math.round(Math.min(280,contentWidth()*.72f+30)*fontScale);
        return Math.max(40,Math.min(available,cap));
    }
    int summaryHeight(){return Math.round(124*fontScale);}
}
