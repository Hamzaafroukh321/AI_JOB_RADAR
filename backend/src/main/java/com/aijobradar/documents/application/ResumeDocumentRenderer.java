package com.aijobradar.documents.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

@Component
public class ResumeDocumentRenderer {
  private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

  public byte[] pdf(ResumeContent content) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        float y = 790;
        y = line(stream, content.headline().text(), 16, y, BOLD);
        y = line(stream, "PROFILE", 11, y - 10, BOLD);
        for (String value : wrap(content.summary().text(), 88))
          y = line(stream, value, 10, y, FONT);
        y = line(stream, "VERIFIED HIGHLIGHTS", 11, y - 10, BOLD);
        for (ResumeContent.Claim claim : content.highlights())
          for (String value : wrap("• " + claim.text(), 88)) y = line(stream, value, 10, y, FONT);
        if (!content.missingRequirements().isEmpty()) {
          y = line(stream, "MISSING / NOT CLAIMED", 11, y - 10, BOLD);
          for (String missing : content.missingRequirements())
            for (String value : wrap("• " + missing, 88)) y = line(stream, value, 10, y, FONT);
        }
      }
      document.save(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to render PDF", exception);
    }
  }

  public byte[] docx(ResumeContent content) {
    try (XWPFDocument document = new XWPFDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      paragraph(document, content.headline().text(), true, 16);
      paragraph(document, "PROFILE", true, 11);
      paragraph(document, content.summary().text(), false, 10);
      paragraph(document, "VERIFIED HIGHLIGHTS", true, 11);
      for (ResumeContent.Claim claim : content.highlights())
        paragraph(document, claim.text(), false, 10).setStyle("ListBullet");
      if (!content.missingRequirements().isEmpty()) {
        paragraph(document, "MISSING / NOT CLAIMED", true, 11);
        for (String missing : content.missingRequirements())
          paragraph(document, missing, false, 10).setStyle("ListBullet");
      }
      document.write(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to render DOCX", exception);
    }
  }

  public String html(ResumeContent content) {
    StringBuilder html = new StringBuilder("<article><h1>");
    html.append(escape(content.headline().text())).append("</h1><h2>Profile</h2><p>");
    html.append(escape(content.summary().text())).append("</p><h2>Verified highlights</h2><ul>");
    content
        .highlights()
        .forEach(item -> html.append("<li>").append(escape(item.text())).append("</li>"));
    html.append("</ul>");
    if (!content.missingRequirements().isEmpty()) {
      html.append("<h2>Missing / not claimed</h2><ul>");
      content
          .missingRequirements()
          .forEach(item -> html.append("<li>").append(escape(item)).append("</li>"));
      html.append("</ul>");
    }
    return html.append("</article>").toString();
  }

  private float line(PDPageContentStream stream, String text, int size, float y, PDType1Font font)
      throws IOException {
    if (y < 45) throw new IllegalArgumentException("Resume content exceeds the ATS template");
    stream.beginText();
    stream.setFont(font, size);
    stream.newLineAtOffset(45, y);
    stream.showText(text.replaceAll("[^\\x20-\\x7E]", "-"));
    stream.endText();
    return y - size - 4;
  }

  private XWPFParagraph paragraph(XWPFDocument document, String text, boolean bold, int size) {
    XWPFParagraph paragraph = document.createParagraph();
    XWPFRun run = paragraph.createRun();
    run.setText(text);
    run.setBold(bold);
    run.setFontSize(size);
    return paragraph;
  }

  private List<String> wrap(String value, int width) {
    List<String> result = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : value.split("\\s+")) {
      if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
        result.add(line.toString());
        line.setLength(0);
      }
      if (!line.isEmpty()) line.append(' ');
      line.append(word);
    }
    if (!line.isEmpty()) result.add(line.toString());
    return result;
  }

  private String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
