package com.aijobradar.profile.application;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class ResumeTextExtractor {
  public ExtractionResult extract(byte[] content, String mimeType) {
    try {
      if ("application/pdf".equals(mimeType)) return extractPdf(content);
      if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          .equals(mimeType)) return extractDocx(content);
      throw new IllegalArgumentException("Unsupported resume type");
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("The resume could not be parsed", exception);
    }
  }

  private ExtractionResult extractPdf(byte[] content) throws Exception {
    List<TextBlock> blocks = new ArrayList<>();
    try (var document = Loader.loadPDF(content)) {
      PDFTextStripper stripper = new PDFTextStripper();
      int paragraph = 0;
      int offset = 0;
      String currentSection = null;
      for (int page = 1; page <= document.getNumberOfPages(); page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        for (String raw : stripper.getText(document).split("\\R")) {
          String text = normalize(raw);
          if (text.isBlank()) continue;
          if (looksLikeSection(text)) currentSection = text;
          int end = offset + text.length();
          blocks.add(new TextBlock(page, paragraph++, currentSection, text, offset, end));
          offset = end + 1;
        }
      }
    }
    return result(blocks);
  }

  private ExtractionResult extractDocx(byte[] content) throws Exception {
    List<TextBlock> blocks = new ArrayList<>();
    try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
      int paragraph = 0;
      int offset = 0;
      String currentSection = null;
      for (var source : document.getParagraphs()) {
        String text = normalize(source.getText());
        if (text.isBlank()) continue;
        if (looksLikeSection(text)) currentSection = text;
        int end = offset + text.length();
        blocks.add(new TextBlock(null, paragraph++, currentSection, text, offset, end));
        offset = end + 1;
      }
    }
    return result(blocks);
  }

  private ExtractionResult result(List<TextBlock> blocks) {
    if (blocks.isEmpty())
      throw new IllegalArgumentException("The resume contains no extractable text");
    String text = String.join("\n", blocks.stream().map(TextBlock::text).toList());
    return new ExtractionResult(text, List.copyOf(blocks));
  }

  private String normalize(String value) {
    return value.replace('\u0000', ' ').replaceAll("[\\t ]+", " ").trim();
  }

  private boolean looksLikeSection(String text) {
    if (text.length() > 60 || text.contains(".")) return false;
    String normalized = text.toUpperCase(Locale.ROOT);
    return normalized.equals(text)
        || normalized.matches(
            "(EXPERIENCE|EDUCATION|SKILLS|PROJECTS|CERTIFICATIONS|LANGUAGES|SUMMARY|PROFILE)");
  }

  public record ExtractionResult(String text, List<TextBlock> blocks) {}

  public record TextBlock(
      Integer page, int paragraph, String section, String text, int startOffset, int endOffset) {}
}
