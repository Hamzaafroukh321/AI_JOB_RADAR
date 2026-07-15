package com.aijobradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aijobradar.ai.application.LanguageModelProvider;
import com.aijobradar.storage.application.ObjectStorage;
import com.aijobradar.users.infrastructure.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FoundationIT.StorageTestConfiguration.class)
class FoundationIT {
  private static final String SEED_EMAIL = "phase0@example.test";
  private static final String SEED_PASSWORD = "Synthetic-test-password-42";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17.5-alpine")
          .withDatabaseName("aijobradar")
          .withUsername("radar_test")
          .withPassword("container-only");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("radar.seed.enabled", () -> true);
    registry.add("radar.seed.email", () -> SEED_EMAIL);
    registry.add("radar.seed.password", () -> SEED_PASSWORD);
    registry.add("radar.seed.display-name", () -> "Synthetic Test User");
    registry.add("radar.storage.endpoint", () -> "http://localhost:9");
    registry.add("radar.storage.access-key", () -> "minio-test");
    registry.add("radar.storage.secret-key", () -> "minio-test-secret");
    registry.add("radar.storage.bucket", () -> "phase0-test");
  }

  @Autowired MockMvc mvc;
  @Autowired UserAccountRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired LanguageModelProvider aiProvider;
  @Autowired ObjectMapper json;
  @Autowired ObjectStorage testStorage;

  @Test
  void flywayMigratesEmptyPostgresAndSeedIsCreatedOnce() {
    Integer migrations =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class);
    assertThat(migrations).isGreaterThanOrEqualTo(1);
    assertThat(users.findByEmail(SEED_EMAIL)).isPresent();
    assertThat(users.findAll()).hasSize(1);
  }

  @Test
  void unauthenticatedAccessIsRejectedWithSafeProblemDetails() throws Exception {
    mvc.perform(get("/api/v1/dashboard/summary"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required"));
  }

  @Test
  void loginRequiresCsrfAndSupportsProtectedSessionAndLogout() throws Exception {
    String body = "{\"email\":\"" + SEED_EMAIL + "\",\"password\":\"" + SEED_PASSWORD + "\"}";
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());

    MvcResult login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(SEED_EMAIL))
            .andReturn();
    Cookie session = login.getResponse().getCookie("SESSION");
    assertThat(session).isNotNull();
    assertThat(session.isHttpOnly()).isTrue();

    mvc.perform(get("/api/v1/dashboard/summary").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("PHASE_0"))
        .andExpect(jsonPath("$.user.email").value(SEED_EMAIL));

    mvc.perform(post("/api/v1/auth/logout").cookie(session)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void failedLoginAndValidationUseGenericErrors() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + SEED_EMAIL + "\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password_hash"))));
  }

  @Test
  void corsHealthOpenApiCorrelationAndDisabledAiAreConfigured() throws Exception {
    mvc.perform(
            options("/api/v1/auth/login")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-ID"));
    assertThat(aiProvider.isEnabled()).isFalse();
  }

  @Test
  void docxUploadFactReviewProfileVersionAndUserScopeWork() throws Exception {
    Cookie session = login();
    long initialVersion =
        json.readTree(
                mvc.perform(get("/api/v1/profile").cookie(session))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("profileVersion")
            .asLong();
    byte[] content = docx("EXPERIENCE", "Built a synthetic secure Java service.");
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "synthetic-resume.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            content);
    MvcResult uploaded =
        mvc.perform(
                multipart("/api/v1/master-resumes")
                    .file(file)
                    .param("name", "Synthetic Resume")
                    .cookie(session)
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.extractionStatus").value("EXTRACTED"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.blocks[0].page").doesNotExist())
            .andReturn();
    UUID resumeId =
        UUID.fromString(
            json.readTree(uploaded.getResponse().getContentAsString()).get("id").asText());
    mvc.perform(
            put("/api/v1/master-resumes/{id}/language", resumeId)
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languageCode\":\"fr\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.languageCode").value("fr"));
    String storageKey =
        jdbc.queryForObject(
            "select storage_key from document_files where user_id=(select id from app_users where email=?)",
            String.class,
            SEED_EMAIL);
    assertThat(testStorage.get(storageKey)).isEqualTo(content);

    JsonNode allFacts =
        json.readTree(
            mvc.perform(get("/api/v1/candidate-facts").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode proposed = null;
    for (JsonNode candidate : allFacts) {
      if (resumeId.toString().equals(candidate.path("masterResumeId").asText())) {
        proposed = candidate;
        break;
      }
    }
    assertThat(proposed).isNotNull();
    UUID factId = UUID.fromString(proposed.get("id").asText());
    mvc.perform(post("/api/v1/candidate-facts/{id}/verify", factId).cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
    mvc.perform(get("/api/v1/candidate-facts").param("status", "VERIFIED").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(factId.toString()));

    String edit =
        """
        {"masterResumeId":"%s","factType":"EXPERIENCE_BULLET",
         "statement":"Built and tested a synthetic secure Java service.","skills":["Java"]}
        """
            .formatted(resumeId);
    mvc.perform(
            put("/api/v1/candidate-facts/{id}", factId)
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
        .andExpect(jsonPath("$.userEdited").value(true))
        .andExpect(jsonPath("$.supersedesFactId").value(factId.toString()));
    assertThat(
            jdbc.queryForObject(
                "select verification_status from candidate_facts where id=?", String.class, factId))
        .isEqualTo("SUPERSEDED");

    mvc.perform(multipart("/api/v1/master-resumes").file(file).cookie(session).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/master-resumes/{id}", UUID.randomUUID()).cookie(session))
        .andExpect(status().isNotFound());
    long finalVersion =
        json.readTree(
                mvc.perform(get("/api/v1/profile").cookie(session))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("profileVersion")
            .asLong();
    assertThat(finalVersion).isGreaterThan(initialVersion);
  }

  @Test
  void pdfLanguagesAuthorizationsAndSeedImportRemainExplicit() throws Exception {
    Cookie session = login();
    MockMultipartFile pdf =
        new MockMultipartFile(
            "file", "synthetic.pdf", "application/pdf", pdf("SKILLS", "Java and Angular"));
    mvc.perform(multipart("/api/v1/master-resumes").file(pdf).cookie(session).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mimeType").value("application/pdf"))
        .andExpect(jsonPath("$.blocks[0].page").value(1));

    mvc.perform(
            post("/api/v1/profile/languages")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"languageCode\":\"en\",\"spokenLevel\":\"PROFESSIONAL\",\"writtenLevel\":\"FLUENT\",\"professionalUse\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.verifiedByUser").value(true));
    mvc.perform(
            post("/api/v1/profile/authorizations")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"countryCode\":\"MA\",\"authorizationStatus\":\"CITIZEN\",\"sponsorshipNeeded\":false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.verifiedByUser").value(true));
    mvc.perform(post("/api/v1/profile/import-seed").cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imported").value(org.hamcrest.Matchers.greaterThan(0)));
    mvc.perform(post("/api/v1/profile/import-seed").cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imported").value(0));
    Integer inferred =
        jdbc.queryForObject(
            "select count(*) from candidate_languages where verified_by_user=false", Integer.class);
    assertThat(inferred).isZero();
  }

  @Test
  void phaseTwoMigrationManualImportAndSourceComplianceAreIdempotent() throws Exception {
    Cookie session = login();
    String manual =
        """
        {"title":"Synthetic ML Engineer","company":"Synthetic Labs","location":"Remote",
         "sourceUrl":"https://example.test/jobs/synthetic?utm_source=fixture",
         "applicationUrl":"https://example.test/jobs/synthetic/apply",
         "description":"Build synthetic ML systems. USD 90000-110000 per year.",
         "employmentType":"Full time","workplaceMode":"Remote"}
        """;
    mvc.perform(
            post("/api/v1/sources/manual-import")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(manual))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(true));
    mvc.perform(
            post("/api/v1/sources/manual-import")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(manual))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(false));

    Integer privateJobs =
        jdbc.queryForObject(
            "select count(*) from jobs where owner_user_id=(select id from app_users where email=?) and original_title='Synthetic ML Engineer'",
            Integer.class,
            SEED_EMAIL);
    assertThat(privateJobs).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from raw_job_records where external_id in (select external_id from job_source_occurrences where job_source_id='00000000-0000-0000-0000-000000000201')",
                Integer.class))
        .isGreaterThanOrEqualTo(2);

    String sourceKey = "fixture-" + UUID.randomUUID();
    String source =
        """
        {"key":"%s","displayName":"Fixture Company","type":"GREENHOUSE",
         "termsUrl":"https://developers.greenhouse.io/job-board.html",
         "termsReviewStatus":"REVIEW_REQUIRED","credentialsRequired":false,
         "timezone":"Africa/Casablanca","priority":100,"parserVersion":"1.0.0",
         "configuration":{"boardToken":"fixture"}}
        """
            .formatted(sourceKey);
    MvcResult created =
        mvc.perform(
                post("/api/v1/sources")
                    .cookie(session)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(source))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enabled").value(false))
            .andReturn();
    UUID sourceId =
        UUID.fromString(
            json.readTree(created.getResponse().getContentAsString()).get("id").asText());
    mvc.perform(post("/api/v1/sources/{id}/enable", sourceId).cookie(session).with(csrf()))
        .andExpect(status().isConflict());

    String secretSource =
        source
            .replace(sourceKey, sourceKey + "-secret")
            .replace("\"boardToken\":\"fixture\"", "\"apiKey\":\"gsk_not-a-real-secret\"");
    mvc.perform(
            post("/api/v1/sources")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(secretSource))
        .andExpect(status().isBadRequest());
  }

  @Test
  void phaseThreeClassifiesSearchesAndScopesUserJobState() throws Exception {
    Cookie session = login();
    String manual =
        """
        {"title":"Worldwide Coding Evaluator","company":"Evidence Labs","location":"Remote",
         "sourceUrl":"https://example.test/jobs/worldwide-evaluator",
         "description":"Work remotely worldwide. Evaluate LLM responses, write coding rubrics, and perform model evaluation in Java.",
         "employmentType":"Contract","workplaceMode":"Remote"}
        """;
    MvcResult imported =
        mvc.perform(
                post("/api/v1/sources/manual-import")
                    .cookie(session)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(manual))
            .andExpect(status().isCreated())
            .andReturn();
    UUID jobId =
        UUID.fromString(
            json.readTree(imported.getResponse().getContentAsString()).get("jobId").asText());

    mvc.perform(
            get("/api/v1/jobs")
                .cookie(session)
                .param("section", "WORLDWIDE_REMOTE")
                .param("q", "Coding Evaluator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(jobId.toString()))
        .andExpect(jsonPath("$.content[0].remoteScope").value("WORLDWIDE"))
        .andExpect(jsonPath("$.content[0].annotationRelevance").value("HIGH"));
    mvc.perform(get("/api/v1/jobs/{id}", jobId).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysisValidationStatus").value("DETERMINISTIC"))
        .andExpect(jsonPath("$.analysis.technologies[0]").value("Java"))
        .andExpect(
            jsonPath("$.regions")
                .value(
                    org.hamcrest.Matchers.hasItems(
                        "ALL_AI", "AI_TRAINING_DATA", "WORLDWIDE_REMOTE")));

    mvc.perform(post("/api/v1/jobs/{id}/save", jobId).cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true));
    mvc.perform(post("/api/v1/jobs/{id}/hide", jobId).cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hidden").value(true));
    mvc.perform(get("/api/v1/jobs").cookie(session).param("q", "Worldwide Coding Evaluator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(post("/api/v1/jobs/{id}/restore", jobId).cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hidden").value(false));
    mvc.perform(delete("/api/v1/jobs/{id}/save", jobId).cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(false));
    mvc.perform(post("/api/v1/jobs/{id}/reanalyze", jobId).cookie(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysisId").exists());
  }

  private Cookie login() throws Exception {
    String body = "{\"email\":\"" + SEED_EMAIL + "\",\"password\":\"" + SEED_PASSWORD + "\"}";
    return mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("SESSION");
  }

  private static byte[] docx(String... paragraphs) throws Exception {
    try (var document = new XWPFDocument();
        var output = new ByteArrayOutputStream()) {
      for (String paragraph : paragraphs) document.createParagraph().createRun().setText(paragraph);
      document.write(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdf(String... lines) throws Exception {
    try (var document = new PDDocument();
        var output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(FontName.HELVETICA), 12);
        content.newLineAtOffset(40, 700);
        for (String line : lines) {
          content.showText(line);
          content.newLineAtOffset(0, -20);
        }
        content.endText();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  @TestConfiguration
  static class StorageTestConfiguration {
    @Bean
    @Primary
    ObjectStorage testObjectStorage() {
      return new ObjectStorage() {
        private final java.util.concurrent.ConcurrentMap<String, byte[]> objects =
            new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public boolean isAvailable() {
          return true;
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
          objects.putIfAbsent(key, content.clone());
        }

        @Override
        public byte[] get(String key) {
          byte[] content = objects.get(key);
          if (content == null) throw new IllegalStateException("Test object not found");
          return content.clone();
        }
      };
    }
  }
}
