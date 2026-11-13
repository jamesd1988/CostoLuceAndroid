package com.costoluce.importatore;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.util.Base64;
import java.util.zip.*;
import static org.junit.Assert.*;

public class AreraTableTest {
    // Public ARERA 2026 workbook, restricted to source XML for August/September.
    // Source: https://www.arera.it/fileadmin/area_operatori/prezzi_e_tariffe/Corrispettivi_libero_elettrico_domestico_2026.xlsx
    private byte[] fixture()throws Exception{
        try(InputStream in=getClass().getResourceAsStream("/arera-2026-layout.b64")){
            return Base64.getMimeDecoder().decode(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII));
        }
    }
    private byte[] changed(String from,String to)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipInputStream input=new ZipInputStream(new ByteArrayInputStream(fixture()));ZipOutputStream output=new ZipOutputStream(bytes)){
            ZipEntry e;while((e=input.getNextEntry())!=null){output.putNextEntry(new ZipEntry(e.getName()));String xml=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);output.write(xml.replace(from,to).getBytes(java.nio.charset.StandardCharsets.UTF_8));output.closeEntry();}
        }return bytes.toByteArray();
    }
    @Test public void officialTariffsAndInvoiceNetworkTotalsAgree()throws Exception{
        JSONObject months=AreraTable.parse(fixture(),2026);assertEquals(2,months.length());
        JSONObject aug=months.getJSONObject("2026-08").getJSONObject("resident");
        JSONObject sep=months.getJSONObject("2026-09").getJSONObject("resident");
        assertEquals(.0119,aug.getDouble("transport"),1e-12);assertEquals(.00283,aug.getDouble("equalization"),1e-12);
        assertEquals(.031515,aug.getDouble("asos"),1e-12);assertEquals(.001638,sep.getDouble("arim"),1e-12);
        assertEquals(1.92,aug.getDouble("networkAnnual")/12,1e-12);assertEquals(5.88,aug.getDouble("powerAnnual")*3/12,1e-12);
        assertEquals(.016567,aug.getDouble("qualityPowerAnnual")/12,0.0000005);
        assertEquals(0,aug.getDouble("oneriAnnual"),0);
        assertEquals(95.0916,months.getJSONObject("2026-08").getJSONObject("nonresident").getDouble("oneriAnnual"),1e-8);
        double network=0,asos=0,arim=0;
        for(int k:new int[]{178,160}){network+=round(k*aug.getDouble("transport"))+round(k*aug.getDouble("equalization"))+1.92+5.88+.05;asos+=round(k*aug.getDouble("asos"));arim+=round(k*aug.getDouble("arim"));}
        assertEquals(20.67,network,1e-8);assertEquals(10.65,asos,1e-8);assertEquals(.55,arim,1e-8);
        assertFalse(aug.has("dispatch"));assertFalse(aug.has("balancing"));
    }
    private double round(double v){return Math.round(v*100)/100.0;}
    @Test(expected=IOException.class) public void changedUnitFailsClosed()throws Exception{AreraTable.parse(changed("Quota fissa (euro/anno)","Quota fissa (euro/mese)"),2026);}
    @Test(expected=IOException.class) public void missingHeaderFailsClosed()throws Exception{AreraTable.parse(changed(">SOS<",">UNKNOWN<"),2026);}
    @Test(expected=IOException.class) public void entitiesAreRejected()throws Exception{AreraTable.parse(changed("<sst ","<!DOCTYPE sst [<!ENTITY x SYSTEM 'file:///private'>]><sst "),2026);}
    @Test(expected=IOException.class) public void wrongYearIsNotSubstituted()throws Exception{AreraTable.parse(fixture(),2027);}
}
