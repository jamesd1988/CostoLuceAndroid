package com.costoluce.importatore;
import org.junit.Test;
import static org.junit.Assert.*;
public class IssuedInvoiceTest {
    private String text(){return "Enel Energia POD IT001E00000000 Fattura elettronica n. 123456789 del 12/10/2026\nPeriodo AGO. 2026 - SET. 2026 - spesa per ENERGIA ELETTRICA\nTotale Bolletta\n127,56 €\nCanone TV 18,00 €\nTotale da pagare 145,56 €\nTotale Bolletta 127,56\nTotale da pagare 145,56";}
    @Test public void readsOriginalElectricityWithoutTv()throws Exception{IssuedInvoice i=IssuedInvoice.parse(text());assertEquals(12756,i.electricityCents);assertEquals(14556,i.payableCents);assertEquals("2026-08-01",i.from);assertEquals("2026-09-30",i.to);assertFalse(i.json().has("pod"));}
    @Test public void crossYearPeriod(){IssuedInvoice i=IssuedInvoice.parse(text().replace("AGO. 2026 - SET. 2026","DIC. 2026 - GEN. 2027"));assertEquals("2027-01-31",i.to);}
    @Test(expected=IllegalArgumentException.class)public void noGuessFromPayable(){IssuedInvoice.parse(text().replace("Totale Bolletta","Totale documento"));}
    @Test(expected=IllegalArgumentException.class)public void conflictingTotalRejected(){IssuedInvoice.parse(text()+"\nTotale Bolletta 130,00");}
    @Test(expected=IllegalArgumentException.class)public void multipleSuppliesRejected(){IssuedInvoice.parse(text()+"\nPOD IT001E00000001");}
    @Test(expected=IllegalArgumentException.class)public void unknownPeriodRejected(){IssuedInvoice.parse(text().replace("AGO. 2026 - SET. 2026","01/08/2026"));}
}
