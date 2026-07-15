package com.aijobradar.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class ResumeTextExtractorTest {
  private final ResumeTextExtractor extractor = new ResumeTextExtractor();

  @Test
  void extractsPdfWithPageProvenance() throws Exception {
    byte[] pdf;
    try (var document = new PDDocument();
        var output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(FontName.HELVETICA), 12);
        content.newLineAtOffset(40, 700);
        content.showText("EXPERIENCE");
        content.newLineAtOffset(0, -20);
        content.showText("Built secure Java services.");
        content.endText();
      }
      document.save(output);
      pdf = output.toByteArray();
    }
    var result = extractor.extract(pdf, "application/pdf");
    assertThat(result.text()).contains("Built secure Java services");
    assertThat(result.blocks()).anyMatch(block -> Integer.valueOf(1).equals(block.page()));
  }

  @Test
  void extractsDocxWithParagraphProvenance() throws Exception {
    byte[] docx;
    try (var document = new XWPFDocument();
        var output = new ByteArrayOutputStream()) {
      document.createParagraph().createRun().setText("SKILLS");
      document.createParagraph().createRun().setText("Java and Angular");
      document.write(output);
      docx = output.toByteArray();
    }
    var result =
        extractor.extract(
            docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    assertThat(result.text()).contains("Java and Angular");
    assertThat(result.blocks()).extracting(ResumeTextExtractor.TextBlock::paragraph).contains(0, 1);
  }
}
