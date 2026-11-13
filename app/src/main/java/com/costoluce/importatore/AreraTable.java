package com.costoluce.importatore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.json.JSONObject;

/** Reads the public ARERA workbook. Unknown layouts fail closed, never become zero tariffs. */
final class AreraTable {
    private static final String[] MONTHS={"gennaio","febbraio","marzo","aprile","maggio","giugno","luglio","agosto","settembre","ottobre","novembre","dicembre"};
    static JSONObject parse(byte[] bytes,int year) throws Exception {
        Map<String,byte[]> files=new HashMap<>();int total=0,entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;byte[] buffer=new byte[4096];
            while((entry=zip.getNextEntry())!=null){
                if(++entries>400)throw new IOException("Workbook troppo grande");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
                while((n=zip.read(buffer))!=-1){total+=n;if(total>8_000_000||out.size()+n>2_000_000)throw new IOException("Workbook troppo grande");out.write(buffer,0,n);}
                files.put(entry.getName(),out.toByteArray());
            }
        }
        List<String> strings=new ArrayList<>();
        NodeList shared=xml(files,"xl/sharedStrings.xml").getElementsByTagName("si");
        for(int i=0;i<shared.getLength();i++)strings.add(shared.item(i).getTextContent());
        Map<String,String> targets=new HashMap<>();
        NodeList relationships=xml(files,"xl/_rels/workbook.xml.rels").getElementsByTagName("Relationship");
        for(int i=0;i<relationships.getLength();i++){Element e=(Element)relationships.item(i);targets.put(e.getAttribute("Id"),e.getAttribute("Target"));}
        JSONObject result=new JSONObject();NodeList sheets=xml(files,"xl/workbook.xml").getElementsByTagName("sheet");
        for(int i=0;i<sheets.getLength();i++){
            Element sheet=(Element)sheets.item(i);String name=sheet.getAttribute("name").toLowerCase(Locale.ITALY);int month=0;
            for(int m=0;m<12;m++)if(name.equals(MONTHS[m]+" "+year))month=m+1;
            if(month==0)continue;
            String target=targets.get(sheet.getAttribute("r:id"));
            if(target==null||!target.matches("worksheets/sheet[0-9]+\\.xml"))throw new IOException("Foglio non riconosciuto");
            Map<String,String> cells=new HashMap<>();NodeList nodes=xml(files,"xl/"+target).getElementsByTagName("c");
            for(int j=0;j<nodes.getLength();j++){
                Element cell=(Element)nodes.item(j);NodeList values=cell.getElementsByTagName("v");
                String value=values.getLength()==0?"":values.item(0).getTextContent();
                if("s".equals(cell.getAttribute("t")))value=strings.get(Integer.parseInt(value));
                cells.put(cell.getAttribute("r"),value);
            }
            require(cells,"B2",MONTHS[month-1]+" "+year);require(cells,"B10","ABITAZIONI DI RESIDENZE ANAGRAFICHE");
            require(cells,"B20","ABITAZIONI DIVERSE DA RESIDENZE ANAGRAFICHE");
            for(String c:new String[]{"J12","J22"})require(cells,c,"ASOS");
            for(String c:new String[]{"K12","K22"})require(cells,c,"ARIM");
            for(String c:new String[]{"D12","D22"})require(cells,c,"σ1");
            for(String c:new String[]{"E12","E22"})require(cells,c,"σ2");
            for(String c:new String[]{"F12","F22"})require(cells,c,"σ3");
            for(String c:new String[]{"G12","G22"})require(cells,c,"UC3");
            for(String c:new String[]{"H12","H22"})require(cells,c,"UC6");
            for(int offset:new int[]{0,10}){require(cells,"B"+(14+offset),"Quota energia (euro/kWh)");require(cells,"B"+(15+offset),"Quota fissa (euro/anno)");require(cells,"B"+(16+offset),"Quota potenza (euro/kW/anno)");}
            JSONObject profiles=new JSONObject();
            for(int offset:new int[]{0,10}){
                int r=14+offset;JSONObject p=new JSONObject();
                p.put("transport",number(cells,"F"+r));p.put("equalization",number(cells,"G"+r)+number(cells,"H"+r));
                p.put("asos",number(cells,"J"+r));p.put("arim",number(cells,"K"+r));
                p.put("networkAnnual",number(cells,"D"+(r+1)));p.put("powerAnnual",number(cells,"E"+(r+2)));
                p.put("qualityPowerAnnual",number(cells,"H"+(r+2)));
                // A dash is permitted only here: the resident fixed oneri component is not applicable.
                p.put("oneriAnnual",optional(cells,"J"+(r+1))+optional(cells,"K"+(r+1)));
                profiles.put(offset==0?"resident":"nonresident",p);
            }
            result.put(String.format(Locale.ROOT,"%04d-%02d",year,month),profiles);
        }
        if(result.length()==0)throw new IOException("Nessuna tariffa valida");return result;
    }
    private static Document xml(Map<String,byte[]> files,String path)throws Exception{
        byte[] data=files.get(path);if(data==null)throw new IOException("Tabella incompleta");
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString().toUpperCase(Locale.ROOT);
        if(text.indexOf('\0')>=0||text.contains("<!DOCTYPE")||text.contains("<!ENTITY"))throw new IOException("XML non consentito");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(data));
    }
    private static void require(Map<String,String> cells,String cell,String expected)throws IOException{
        if(!expected.equalsIgnoreCase(cells.get(cell)))throw new IOException("Formato ARERA cambiato");
    }
    private static double optional(Map<String,String> cells,String cell)throws IOException{
        if("-".equals(cells.getOrDefault(cell,"").trim()))return 0;return number(cells,cell);
    }
    private static double number(Map<String,String> cells,String cell)throws IOException{
        try{double value=Double.parseDouble(cells.get(cell));if(!Double.isFinite(value)||value<0||value>1000)throw new Exception();return value;}
        catch(Exception e){throw new IOException("Corrispettivo ARERA non valido");}
    }
}
