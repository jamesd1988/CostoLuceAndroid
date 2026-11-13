package com.costoluce.importatore;
import android.content.Context;
import java.io.IOException;
/** Same on-device parser for downloaded documents and optional file imports. Never retains a PDF. */
final class InvoiceDocuments {
    static IssuedInvoice read(Context context,byte[] bytes)throws Exception{
        if(bytes.length>6_000_000)throw new IOException("PDF troppo grande.");
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.getApplicationContext());
        try(com.tom_roush.pdfbox.pdmodel.PDDocument pdf=com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes)){
            if(pdf.getNumberOfPages()>30||pdf.isEncrypted())throw new IOException("PDF non supportato.");
            com.tom_roush.pdfbox.text.PDFTextStripper text=new com.tom_roush.pdfbox.text.PDFTextStripper();text.setSortByPosition(false);return IssuedInvoice.parse(text.getText(pdf));
        }
    }
}
