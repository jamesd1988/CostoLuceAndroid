package com.costoluce.importatore;
import org.junit.Test;
import static org.junit.Assert.*;
public class WidgetLayoutSpecTest {
    @Test public void shapesUseBothAxesAndRespectFontScale(){
        assertTrue(new WidgetLayoutSpec(130,64).statsOnly());
        assertTrue(new WidgetLayoutSpec(180,180).smallSummary());
        assertTrue(new WidgetLayoutSpec(130,500).fullPages());
        assertTrue(new WidgetLayoutSpec(190,420).horizontalChart());
        assertTrue(new WidgetLayoutSpec(260,252).fullPages());
        assertTrue(new WidgetLayoutSpec(410,210).shortWide());
        assertTrue(new WidgetLayoutSpec(420,450).unified());
        assertFalse(new WidgetLayoutSpec(420,450,1.3f).unified());
        assertFalse(new WidgetLayoutSpec(280,260,1.3f).fullPages());
        assertTrue(new WidgetLayoutSpec(180,180).smallRefresh());
        assertFalse(new WidgetLayoutSpec(96,48).statsRefreshPossible());
        assertTrue(new WidgetLayoutSpec(150,80).statsRefreshPossible());
    }
    @Test public void plotsReserveControlsAndDoNotStretchInTallHosts(){
        WidgetLayoutSpec compact=new WidgetLayoutSpec(280,260),tall=new WidgetLayoutSpec(280,800);
        assertEquals(256,compact.contentWidth());
        assertTrue(compact.chartHeight(1,true)>=80);
        assertTrue(tall.chartHeight(1,false)<250);
        assertTrue(compact.chartHeight(1.3f,true)<compact.chartHeight(1,true));
        assertTrue(new WidgetLayoutSpec(190,420).chartHeight(1,false)>=4*44);
    }
    @Test public void invalidLauncherBoundsAreLimited(){
        WidgetLayoutSpec small=new WidgetLayoutSpec(-100,0),large=new WidgetLayoutSpec(9000,9000);
        assertEquals(64,small.width);assertEquals(32,small.height);
        assertEquals(1600,large.width);assertEquals(1600,large.height);
    }
}
