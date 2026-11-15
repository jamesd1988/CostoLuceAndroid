package com.costoluce.importatore;
import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import static org.junit.Assert.*;
public class WidgetProjectionTest {
    @Test public void nativeForecastMatchesActualAppAlgorithmAndTariffs()throws Exception{
        String source;try(java.io.InputStream in=getClass().getResourceAsStream("/widget-projection-cases.json")){assertNotNull(in);source=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        JSONArray cases=new JSONArray(source);assertTrue(cases.length()>=20);
        for(int i=0;i<cases.length();i++){
            JSONObject c=cases.getJSONObject(i),expected=c.getJSONObject("expected");String name=c.getString("name");
            WidgetProjection.Result result=WidgetProjection.calculate(c.getJSONArray("rows"),c.getInt("months"),c.getInt("anchor"),LocalDate.parse(c.getString("today")),WidgetPricing.parse(c.getJSONObject("rates").toString(),c.getBoolean("resident")));
            assertEquals(name,expected.getBoolean("available"),result.available);
            if(result.available){assertEquals(name,expected.getDouble("value"),result.value,.000001);assertEquals(name,expected.getDouble("kwh"),result.kwh,.000001);assertEquals(name,expected.getDouble("observedKwh"),result.observedKwh,.000001);assertEquals(name,expected.getDouble("average"),result.average,.000001);assertEquals(name,expected.getInt("sampleDays"),result.sampleDays);assertEquals(name,expected.getString("from"),result.from.toString());assertEquals(name,expected.getString("to"),result.to.toString());}
            else assertFalse(name,result.reason.isEmpty());
        }
    }
}
