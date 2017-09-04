/*
  AUTH | hwding
  DATE | Sep 04 2017
  DESC | text stamp remover for PDF files
  MAIL | m@amastigote.com
  GITH | github.com/hwding
 */
package com.amastigote.unstamper.core;

import com.amastigote.unstamper.log.GeneralLogger;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Processor {
    public static void process(File file, String[] strings) {
        GeneralLogger.Processor.procInProgress(file.getName());
        try {
            if (!file.canWrite()) {
                GeneralLogger.File.notWritable(file.getName());
                return;
            }
            PDDocument pdDocument = PDDocument.load(file);
            pdDocument.getPages().forEach(pdPage -> {
                try {
                    /* START: loading font resources from current page */
                    PDFStreamParser pdfStreamParser = new PDFStreamParser(pdPage);
                    pdfStreamParser.parse();

                    Set<PDFont> pdFonts = new HashSet<>();

                    pdPage.getResources().getFontNames().forEach(e -> {
                        /* Ignore Any Exception During Parallel Processing */
                        try {
                            PDFont pdFont = pdPage.getResources().getFont(e);
                            if (pdFont != null)
                                pdFonts.add(pdFont);
                        } catch (Exception ignored) {
                        }
                    });
                    /* END */

                    List<Object> objects = pdfStreamParser.getTokens();
                    objects.parallelStream().forEach(e -> {
                        if (e instanceof COSString) {
                            /* Ignore Any Exception During Parallel Processing */
                            try {
                                if (TextStampRecognizer.recognize(strings, ((COSString) e).getBytes(), pdFonts))
                                    ((COSString) e).setValue(new byte[0]);
                            } catch (Exception ignored) {
                            }
                        }
                    });

                    PDStream newContents = new PDStream(pdDocument);
                    OutputStream out = newContents.createOutputStream();
                    ContentStreamWriter writer = new ContentStreamWriter(out);
                    writer.writeTokens(objects);
                    out.close();

                    pdPage.setContents(newContents);
                } catch (Exception e) {
                    GeneralLogger.Processor.errorProcess(file.getName());
                }
            });
            pdDocument.save(file);
            pdDocument.close();

            GeneralLogger.Processor.procFinished();
        } catch (IOException e) {
            GeneralLogger.Processor.errorLoadPdf(file.getName());
        }
    }
}
