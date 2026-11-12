package com.costoluce.importatore;
import org.junit.Test;
import static org.junit.Assert.*;
public class WidgetLayoutSpecTest {
    @Test public void detailsRequireSpaceOnBothAxes(){
        assertEquals(0,new WidgetLayoutSpec(280,260).level);
        assertEquals(1,new WidgetLayoutSpec(340,300).level);
        assertEquals(2,new WidgetLayoutSpec(400,350).level);
        assertEquals(0,new WidgetLayoutSpec(280,600).level);
        assertEquals(0,new WidgetLayoutSpec(600,260).level);
        assertEquals(1,new WidgetLayoutSpec(400,300).level);
    }
    @Test public void chartUsesAvailableHeightAndReservesControls(){
        WidgetLayoutSpec compact=new WidgetLayoutSpec(280,260),tall=new WidgetLayoutSpec(280,500);
        assertEquals(256,compact.contentWidth());
        assertEquals(121,compact.chartHeight(1,false));
        assertEquals(106,compact.chartHeight(1,true));
        assertEquals(240,tall.chartHeight(1,true)-compact.chartHeight(1,true));
        assertTrue(compact.chartHeight(1.3f,true)<compact.chartHeight(1,true));
    }
    @Test public void invalidLauncherBoundsAreLimited(){
        WidgetLayoutSpec small=new WidgetLayoutSpec(-100,0),large=new WidgetLayoutSpec(9000,9000);
        assertEquals(160,small.width);assertEquals(180,small.height);
        assertEquals(1600,large.width);assertEquals(1600,large.height);
    }
}
