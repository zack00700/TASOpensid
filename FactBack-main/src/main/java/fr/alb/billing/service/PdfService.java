package fr.alb.billing.service;

import java.io.ByteArrayOutputStream;

import jakarta.enterprise.context.ApplicationScoped;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@ApplicationScoped
public class PdfService {
    public byte[] htmlToPdf(String html) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        }
    }
}
