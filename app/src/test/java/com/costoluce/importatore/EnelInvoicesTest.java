package com.costoluce.importatore;
import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class EnelInvoicesTest {
    private static final String POD="IT001E00000000";
    private EnelConnector.Credentials credentials(){return new EnelConnector.Credentials("test@example.invalid","test-password",POD,"123456");}
    private JSONObject cards()throws Exception{return new JSONObject("{\"data\":{\"Cards\":[{\"pod\":\"IT001E00000001\",\"contoContrattuale\":\"other\",\"businessPartner\":\"bp2\",\"codiceFornitura\":\"s2\"},{\"pod\":\"IT001E00000000\",\"contoContrattuale\":\"cc1\",\"businessPartner\":\"bp1\",\"codiceFornitura\":\"s1\"}]}}");}
    private JSONObject archive()throws Exception{return new JSONObject("{\"data\":{\"results\":[{\"contoContrattuale\":\"other\",\"fatture\":[{}]},{\"contoContrattuale\":\"cc1\",\"fatture\":[{\"Numerodocumento\":\"000123456789\",\"Dataemissione\":\""+java.time.LocalDate.now()+"\",\"Importofattura\":\"145,56\"}]}]}}");}
    private IssuedInvoice invoice(){return new IssuedInvoice("123456789",POD,"2026-08-01","2026-09-30",12756,14556);}
    @Test public void exactSupplyAndDocumentParameters()throws Exception{
        EnelInvoices.Supply s=EnelInvoices.supply(cards(),POD,"");assertEquals("cc1",s.account);
        JSONObject request=EnelInvoices.archiveRequest(s);assertEquals("cc1",request.getJSONArray("inputList").getJSONObject(0).getJSONArray("contoContrattuale").getString(0));
        List<JSONObject> docs=EnelInvoices.documents(archive(),s);assertEquals(1,docs.size());String url=EnelInvoices.download(s,docs.get(0),false);
        assertTrue(url.startsWith("https://www.enel.it/bin/areaclienti/auth/downloadFARO?"));assertTrue(url.contains("businessPartner=bp1"));assertTrue(url.contains("numFornitura=s1"));assertFalse(url.contains("other"));
    }
    @Test public void refreshDownloadsAutomaticallyAndSkipsCachedDocuments()throws Exception{
        List<String> requests=new ArrayList<>();final byte[] document="%PDF-test".getBytes(StandardCharsets.UTF_8);
        EnelInvoices.Transport transport=(url,form,max)->{requests.add(url);try{if(url.startsWith(EnelInvoices.CARDS))return cards().toString().getBytes(StandardCharsets.UTF_8);if(url.equals(EnelInvoices.ARCHIVE)){assertTrue(form.startsWith("pagamentiRequest="));return archive().toString().getBytes(StandardCharsets.UTF_8);}return new JSONObject().put("map",new JSONObject().put("data",Base64.getEncoder().encodeToString(document))).toString().getBytes(StandardCharsets.UTF_8);}catch(Exception e){throw new java.io.IOException("fixture");}};
        EnelInvoices.Outcome outcome=EnelInvoices.fetch(credentials(),transport,pdf->{assertArrayEquals(document,pdf);return invoice();},new JSONArray(),System.nanoTime()+30_000_000_000L,null);
        assertTrue(outcome.checked);assertEquals(1,outcome.invoices.size());assertEquals(12756,outcome.invoices.get(0).electricityCents);assertEquals(3,requests.size());
        requests.clear();outcome=EnelInvoices.fetch(credentials(),transport,pdf->{throw new AssertionError("Known document downloaded twice");},new JSONArray().put(invoice().json()),System.nanoTime()+30_000_000_000L,null);
        assertEquals(0,outcome.invoices.size());assertEquals(2,requests.size());assertTrue(outcome.status.contains("1 già archiviate"));
    }
    @Test public void legacyFallbackRemainsReadOnly()throws Exception{
        EnelInvoices.Transport transport=(url,form,max)->{try{if(url.startsWith(EnelInvoices.CARDS))return cards().toString().getBytes(StandardCharsets.UTF_8);if(url.equals(EnelInvoices.ARCHIVE))return archive().toString().getBytes(StandardCharsets.UTF_8);if(url.contains("downloadFARO"))throw new java.io.IOException("not supported");assertTrue(url.contains("action=download-fattura"));assertNull(form);return "%PDF-test".getBytes(StandardCharsets.UTF_8);}catch(JSONException e){throw new java.io.IOException("fixture");}catch(Exception e){if(e instanceof java.io.IOException)throw (java.io.IOException)e;throw new java.io.IOException("fixture");}};
        assertEquals(1,EnelInvoices.fetch(credentials(),transport,pdf->invoice(),new JSONArray(),System.nanoTime()+30_000_000_000L,null).invoices.size());
    }
    @Test public void mismatchedDocumentsAreNotImported()throws Exception{
        EnelInvoices.Transport transport=(url,form,max)->{try{return url.startsWith(EnelInvoices.CARDS)?cards().toString().getBytes(StandardCharsets.UTF_8):url.equals(EnelInvoices.ARCHIVE)?archive().toString().getBytes(StandardCharsets.UTF_8):"%PDF-test".getBytes(StandardCharsets.UTF_8);}catch(Exception e){throw new java.io.IOException("fixture");}};
        EnelInvoices.Outcome outcome=EnelInvoices.fetch(credentials(),transport,pdf->new IssuedInvoice("123456789","IT001E00000001","2026-08-01","2026-09-30",12756,14556),new JSONArray(),System.nanoTime()+30_000_000_000L,null);
        assertTrue(outcome.checked);assertTrue(outcome.invoices.isEmpty());assertTrue(outcome.status.contains("1 non utilizzabili"));
    }
    @Test public void archiveFailureDoesNotLeakMessagesOrReplaceInvoices(){
        EnelInvoices.Outcome outcome=EnelInvoices.fetch(credentials(),(url,form,max)->{throw new java.io.IOException("SENSITIVE_PAYLOAD");},pdf->invoice(),new JSONArray(),System.nanoTime()+30_000_000_000L,null);
        assertFalse(outcome.checked);assertTrue(outcome.needsFiscalCode);assertTrue(outcome.invoices.isEmpty());assertFalse(outcome.status.contains("SENSITIVE"));
    }
    @Test(expected=JSONException.class)public void loginHtmlIsNotPdf()throws Exception{EnelInvoices.pdf("<html>Login required</html>".getBytes(StandardCharsets.UTF_8));}
    @Test(expected=JSONException.class)public void ambiguousSupplyIsRejected()throws Exception{JSONObject root=cards();root.getJSONObject("data").getJSONArray("Cards").put(root.getJSONObject("data").getJSONArray("Cards").getJSONObject(1));EnelInvoices.supply(root,POD,"");}
    @Test public void invoiceResultSurvivesUnavailableConsumption()throws Exception{
        EnelConnector.Result result=new EnelConnector.Result(0,"",0,Collections.emptyList());result.consumptionAvailable=false;result.invoiceOutcome=new EnelInvoices.Outcome();result.invoiceOutcome.invoices.add(invoice());
        EnelConnector.Result transformed=EnelConnector.forBillingWindow(result,BillingPeriod.today(2,10));
        assertFalse(transformed.consumptionAvailable);assertEquals(1,transformed.invoiceOutcome.invoices.size());
    }

}
