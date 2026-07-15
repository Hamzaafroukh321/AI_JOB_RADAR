package com.aijobradar.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aijobradar.documents.application.DeterministicResumePlanner.Fact;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ResumeDocumentContractTest {
  private final DeterministicResumePlanner planner = new DeterministicResumePlanner();
  private final ResumeFactValidator validator = new ResumeFactValidator();
  private final ResumeDocumentRenderer renderer = new ResumeDocumentRenderer();

  @Test
  void unsupportedClaimCannotPassValidation() {
    UUID verified = UUID.randomUUID();
    ResumeContent content =
        new ResumeContent(
            new ResumeContent.Claim("AI Engineer", List.of(verified)),
            new ResumeContent.Claim("Built Kubernetes platforms", List.of(UUID.randomUUID())),
            List.of(),
            List.of("Kubernetes"));

    assertThatThrownBy(() -> validator.validate(content, Set.of(verified)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
  }

  @Test
  void allFiveVariantsUseOnlyCitedFacts() {
    UUID id = UUID.randomUUID();
    List<Fact> facts =
        List.of(
            new Fact(id, "Built Java and RAG services for LLM evaluation", List.of("Java", "RAG")));

    assertThat(DeterministicResumePlanner.VARIANTS).hasSize(5);
    for (String variant : DeterministicResumePlanner.VARIANTS) {
      ResumeContent content = planner.plan(variant, "AI Engineer", facts, List.of("Kubernetes"));
      validator.validate(content, Set.of(id));
      assertThat(content.missingRequirements()).contains("Kubernetes");
    }
  }

  @Test
  void renderedPdfHasSelectableTextAndDocxHasParagraphText() throws Exception {
    UUID id = UUID.randomUUID();
    ResumeContent content =
        planner.plan(
            "GENAI_RAG",
            "Generative AI Engineer",
            List.of(new Fact(id, "Built production RAG services", List.of("RAG"))),
            List.of());

    byte[] pdf = renderer.pdf(content);
    byte[] docx = renderer.docx(content);

    try (var document = Loader.loadPDF(pdf)) {
      assertThat(new PDFTextStripper().getText(document))
          .contains("Generative AI", "Built production RAG services");
    }
    try (var document = new XWPFDocument(new ByteArrayInputStream(docx))) {
      assertThat(document.getParagraphs().stream().map(p -> p.getText()).toList())
          .anyMatch(text -> text.contains("Built production RAG services"));
    }
  }

  @Test
  void htmlPreviewEscapesUntrustedText() {
    UUID id = UUID.randomUUID();
    ResumeContent content =
        new ResumeContent(
            new ResumeContent.Claim("<script>alert(1)</script>", List.of(id)),
            new ResumeContent.Claim("Safe & verified", List.of(id)),
            List.of(),
            List.of());

    assertThat(renderer.html(content))
        .doesNotContain("<script>")
        .contains("&lt;script&gt;", "Safe &amp; verified");
  }

  @Test
  void lockedVersionIsNotEditable() {
    ResumeVersionPolicy policy = new ResumeVersionPolicy();
    policy.requireEditable("DRAFT");
    policy.requireEditable("APPROVED");
    assertThatThrownBy(() -> policy.requireEditable("LOCKED"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Locked resume versions cannot be edited");
  }
}
