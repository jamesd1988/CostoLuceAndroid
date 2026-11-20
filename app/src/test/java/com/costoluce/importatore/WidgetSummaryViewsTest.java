package com.costoluce.importatore;
import org.junit.Test;
import static org.junit.Assert.*;
public class WidgetSummaryViewsTest {
    @Test public void suppliedShapesChooseTwoRowsStackAndGrid(){
        assertEquals(WidgetSummaryViews.TWO_ROWS,WidgetSummaryViews.arrangement(114,82,1));
        assertEquals(WidgetSummaryViews.FOUR_ROWS,WidgetSummaryViews.arrangement(114,162,1));
        assertEquals(WidgetSummaryViews.GRID,WidgetSummaryViews.arrangement(174,162,1));
        assertEquals(WidgetSummaryViews.GRID,WidgetSummaryViews.arrangement(226,66,1));
        assertEquals(WidgetSummaryViews.TWO_ROWS,WidgetSummaryViews.arrangement(114,142,2));
    }
}
