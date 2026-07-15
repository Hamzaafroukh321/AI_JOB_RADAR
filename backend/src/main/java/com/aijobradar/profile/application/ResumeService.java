package com.aijobradar.profile.application;

import com.aijobradar.common.config.RadarProperties;
import com.aijobradar.profile.api.ProfileModels.CandidateFact;
import com.aijobradar.profile.api.ProfileModels.CandidateFactInput;
import com.aijobradar.profile.api.ProfileModels.MasterResume;
import com.aijobradar.profile.api.ProfileModels.TextBlock;
import com.aijobradar.storage.application.ObjectStorage;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ResumeService {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final ObjectStorage storage;
  private final AntivirusScanner antivirus;
  private final UploadPolicy uploadPolicy;
  private final ResumeTextExtractor extractor;
  private final ProfileService profiles;
  private final RadarProperties properties;
  private final Clock clock;
  private final long maxUploadBytes;

  public ResumeService(
      JdbcClient jdbc,
      ObjectMapper json,
      ObjectStorage storage,
      AntivirusScanner antivirus,
      UploadPolicy uploadPolicy,
      ResumeTextExtractor extractor,
      ProfileService profiles,
      RadarProperties properties,
      Clock clock,
      @Value("${MAX_UPLOAD_SIZE:10MB}") DataSize maxUploadSize) {
    this.jdbc = jdbc;
    this.json = json;
    this.storage = storage;
    this.antivirus = antivirus;
    this.uploadPolicy = uploadPolicy;
    this.extractor = extractor;
    this.profiles = profiles;
    this.properties = properties;
    this.clock = clock;
    this.maxUploadBytes = maxUploadSize.toBytes();
  }

  @Transactional
  public MasterResume upload(UUID userId, MultipartFile file, String name, String languageCode) {
    profiles.getProfile(userId);
    try {
      if (file.getSize() > maxUploadBytes)
        throw new IllegalArgumentException("Resume exceeds the configured upload limit");
      byte[] content = file.getBytes();
      var validated =
          uploadPolicy.validate(file.getOriginalFilename(), file.getContentType(), content);
      String sha256 = sha256(content);
      boolean duplicate =
          jdbc.sql(
                  """
                  SELECT EXISTS(SELECT 1 FROM document_files
                    WHERE user_id=:userId AND sha256=:sha AND kind='MASTER_RESUME' AND deleted_at IS NULL)
                  """)
              .param("userId", userId)
              .param("sha", sha256)
              .query(Boolean.class)
              .single();
      if (duplicate)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "This resume was already uploaded");
      var scan = antivirus.scan(content, validated.filename());
      if (scan == AntivirusScanner.ScanResult.REJECTED)
        throw new IllegalArgumentException("Resume was rejected by the security scanner");
      var extraction = extractor.extract(content, validated.mimeType());
      UUID documentId = UUID.randomUUID();
      UUID resumeId = UUID.randomUUID();
      String storageKey =
          "users/" + userId + "/master-resumes/" + documentId + "/" + validated.filename();
      storage.put(storageKey, content, validated.mimeType());
      var now = now();
      jdbc.sql(
              """
              INSERT INTO document_files(id,user_id,kind,original_filename,mime_type,size_bytes,sha256,
                storage_key,scan_status,created_at)
              VALUES (:id,:userId,'MASTER_RESUME',:filename,:mime,:size,:sha,:key,:scan,:now)
              """)
          .param("id", documentId)
          .param("userId", userId)
          .param("filename", validated.filename())
          .param("mime", validated.mimeType())
          .param("size", content.length)
          .param("sha", sha256)
          .param("key", storageKey)
          .param("scan", scan.name())
          .param("now", now)
          .update();
      boolean first = resumes(userId).isEmpty();
      jdbc.sql(
              """
              INSERT INTO master_resumes(id,user_id,document_file_id,name,language_code,extracted_text,
                extraction_status,active,created_at,updated_at,version)
              VALUES (:id,:userId,:documentId,:name,:language,:text,'EXTRACTED',:active,:now,:now,0)
              """)
          .param("id", resumeId)
          .param("userId", userId)
          .param("documentId", documentId)
          .param("name", normalizedName(name, validated.filename()))
          .param("language", normalizedLanguage(languageCode))
          .param("text", extraction.text())
          .param("active", first)
          .param("now", now)
          .update();
      if (first) {
        jdbc.sql(
                "UPDATE candidate_profiles SET active_master_resume_id=:resumeId WHERE user_id=:userId")
            .param("resumeId", resumeId)
            .param("userId", userId)
            .update();
      }
      persistBlocksAndProposals(userId, resumeId, extraction);
      profiles.bump(
          userId,
          first ? "MASTER_RESUME_UPLOADED_AND_ACTIVATED" : "MASTER_RESUME_UPLOADED",
          resumeId);
      return resume(userId, resumeId);
    } catch (ResponseStatusException | IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("The resume could not be uploaded", exception);
    }
  }

  @Transactional(readOnly = true)
  public List<MasterResume> resumes(UUID userId) {
    return jdbc.sql(
            """
            SELECT mr.id,mr.name,mr.language_code,df.original_filename,df.mime_type,df.size_bytes,
              df.sha256,mr.extraction_status,mr.active,mr.created_at,mr.extracted_text
            FROM master_resumes mr JOIN document_files df ON df.id=mr.document_file_id
            WHERE mr.user_id=:userId ORDER BY mr.created_at DESC
            """)
        .param("userId", userId)
        .query((rs, row) -> mapResume(rs, List.of()))
        .list();
  }

  @Transactional(readOnly = true)
  public MasterResume resume(UUID userId, UUID resumeId) {
    List<TextBlock> blocks =
        jdbc.sql(
                """
                SELECT page_number,paragraph_number,section_name,text_content,start_offset,end_offset
                FROM document_text_blocks WHERE user_id=:userId AND master_resume_id=:resumeId
                ORDER BY paragraph_number
                """)
            .param("userId", userId)
            .param("resumeId", resumeId)
            .query(
                (rs, row) ->
                    new TextBlock(
                        rs.getObject("page_number", Integer.class),
                        rs.getInt("paragraph_number"),
                        rs.getString("section_name"),
                        rs.getString("text_content"),
                        rs.getInt("start_offset"),
                        rs.getInt("end_offset")))
            .list();
    return jdbc.sql(
            """
            SELECT mr.id,mr.name,mr.language_code,df.original_filename,df.mime_type,df.size_bytes,
              df.sha256,mr.extraction_status,mr.active,mr.created_at,mr.extracted_text
            FROM master_resumes mr JOIN document_files df ON df.id=mr.document_file_id
            WHERE mr.id=:resumeId AND mr.user_id=:userId
            """)
        .param("resumeId", resumeId)
        .param("userId", userId)
        .query((rs, row) -> mapResume(rs, blocks))
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
  }

  @Transactional
  public MasterResume activate(UUID userId, UUID resumeId) {
    resume(userId, resumeId);
    jdbc.sql(
            "UPDATE master_resumes SET active=false,updated_at=:now WHERE user_id=:userId AND active")
        .param("now", now())
        .param("userId", userId)
        .update();
    jdbc.sql(
            "UPDATE master_resumes SET active=true,updated_at=:now,version=version+1 WHERE id=:id AND user_id=:userId")
        .param("now", now())
        .param("id", resumeId)
        .param("userId", userId)
        .update();
    jdbc.sql("UPDATE candidate_profiles SET active_master_resume_id=:id WHERE user_id=:userId")
        .param("id", resumeId)
        .param("userId", userId)
        .update();
    profiles.bump(userId, "MASTER_RESUME_ACTIVATED", resumeId);
    return resume(userId, resumeId);
  }

  @Transactional
  public MasterResume updateLanguage(UUID userId, UUID resumeId, String languageCode) {
    resume(userId, resumeId);
    String language = normalizedLanguage(languageCode);
    jdbc.sql(
            "UPDATE master_resumes SET language_code=:language,updated_at=:now,version=version+1 WHERE id=:id AND user_id=:userId")
        .param("language", language)
        .param("now", now())
        .param("id", resumeId)
        .param("userId", userId)
        .update();
    profiles.bump(userId, "MASTER_RESUME_LANGUAGE_UPDATED", resumeId);
    return resume(userId, resumeId);
  }

  @Transactional(readOnly = true)
  public List<CandidateFact> facts(UUID userId, String status) {
    String normalized = status == null ? null : status.toUpperCase(Locale.ROOT);
    var query =
        normalized == null
            ? jdbc.sql(
                    "SELECT * FROM candidate_facts WHERE user_id=:userId ORDER BY created_at DESC")
                .param("userId", userId)
            : jdbc.sql(
                    """
                    SELECT * FROM candidate_facts WHERE user_id=:userId
                      AND verification_status=:status ORDER BY created_at DESC
                    """)
                .param("userId", userId)
                .param("status", normalized);
    return query.query((rs, row) -> mapFact(rs)).list();
  }

  @Transactional(readOnly = true)
  public List<CandidateFact> verifiedFactsForGeneration(UUID userId) {
    return facts(userId, "VERIFIED");
  }

  @Transactional
  public CandidateFact createFact(UUID userId, CandidateFactInput input) {
    if (input.masterResumeId() != null) resume(userId, input.masterResumeId());
    UUID id = UUID.randomUUID();
    insertFact(userId, id, input, "VERIFIED", true, null);
    profiles.bump(userId, "VERIFIED_FACT_CREATED", id);
    return requireFact(userId, id);
  }

  @Transactional
  public CandidateFact editFact(UUID userId, UUID id, CandidateFactInput input) {
    CandidateFact prior = requireFact(userId, id);
    if ("SUPERSEDED".equals(prior.verificationStatus()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Superseded facts cannot be edited");
    UUID replacement = UUID.randomUUID();
    insertFact(userId, replacement, input, "VERIFIED", true, id);
    jdbc.sql(
            """
            UPDATE candidate_facts SET verification_status='SUPERSEDED',updated_at=:now,version=version+1
            WHERE id=:id AND user_id=:userId
            """)
        .param("now", now())
        .param("id", id)
        .param("userId", userId)
        .update();
    profiles.bump(userId, "FACT_EDITED", replacement);
    return requireFact(userId, replacement);
  }

  @Transactional
  public CandidateFact transition(UUID userId, UUID id, String target) {
    CandidateFact prior = requireFact(userId, id);
    if ("SUPERSEDED".equals(prior.verificationStatus()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Superseded facts cannot change state");
    String status = target.toUpperCase(Locale.ROOT);
    if (!List.of("VERIFIED", "REJECTED", "NEEDS_CLARIFICATION").contains(status))
      throw new IllegalArgumentException("Unsupported candidate fact state");
    jdbc.sql(
            """
            UPDATE candidate_facts SET verification_status=:status,updated_at=:now,version=version+1
            WHERE id=:id AND user_id=:userId
            """)
        .param("status", status)
        .param("now", now())
        .param("id", id)
        .param("userId", userId)
        .update();
    profiles.bump(userId, "FACT_" + status, id);
    return requireFact(userId, id);
  }

  @Transactional
  public int importCandidateSeed(UUID userId) {
    if (!"development".equalsIgnoreCase(properties.environment()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Candidate seed is development-only");
    profiles.getProfile(userId);
    int imported = 0;
    for (SeedFact seed : seedFacts()) {
      imported +=
          jdbc.sql(
                  """
                  INSERT INTO candidate_facts(id,user_id,fact_type,organization,role_title,statement,
                    skills_json,verification_status,user_edited,created_at,updated_at,version)
                  SELECT :id,:userId,:type,:organization,:role,:statement,CAST(:skills AS jsonb),
                    'PROPOSED',false,:now,:now,0
                  WHERE NOT EXISTS(SELECT 1 FROM candidate_facts WHERE user_id=:userId
                    AND master_resume_id IS NULL AND statement=:statement)
                  """)
              .param("id", UUID.randomUUID())
              .param("userId", userId)
              .param("type", seed.type())
              .param("organization", seed.organization())
              .param("role", seed.role())
              .param("statement", seed.statement())
              .param("skills", write(seed.skills()))
              .param("now", now())
              .update();
    }
    if (imported > 0) profiles.bump(userId, "CANDIDATE_SEED_IMPORTED", null);
    return imported;
  }

  private void persistBlocksAndProposals(
      UUID userId, UUID resumeId, ResumeTextExtractor.ExtractionResult extraction) {
    var now = now();
    int proposals = 0;
    for (var block : extraction.blocks()) {
      jdbc.sql(
              """
              INSERT INTO document_text_blocks(id,user_id,master_resume_id,page_number,paragraph_number,
                section_name,text_content,start_offset,end_offset,created_at)
              VALUES (:id,:userId,:resumeId,:page,:paragraph,:section,:text,:start,:end,:now)
              """)
          .param("id", UUID.randomUUID())
          .param("userId", userId)
          .param("resumeId", resumeId)
          .param("page", block.page())
          .param("paragraph", block.paragraph())
          .param("section", block.section())
          .param("text", block.text())
          .param("start", block.startOffset())
          .param("end", block.endOffset())
          .param("now", now)
          .update();
      if (proposals < 200 && !block.text().equalsIgnoreCase(block.section())) {
        CandidateFactInput input =
            new CandidateFactInput(
                resumeId,
                factType(block.section()),
                null,
                null,
                block.text(),
                null,
                null,
                List.of(),
                block.page(),
                block.startOffset(),
                block.endOffset());
        insertFact(userId, UUID.randomUUID(), input, "PROPOSED", false, null);
        proposals++;
      }
    }
  }

  private void insertFact(
      UUID userId,
      UUID id,
      CandidateFactInput input,
      String status,
      boolean userEdited,
      UUID supersedes) {
    if (input.endDate() != null
        && input.startDate() != null
        && input.endDate().isBefore(input.startDate()))
      throw new IllegalArgumentException("Fact end date cannot precede its start date");
    jdbc.sql(
            """
            INSERT INTO candidate_facts(id,user_id,master_resume_id,fact_type,organization,role_title,
              statement,start_date,end_date,skills_json,source_page,source_start_offset,source_end_offset,
              verification_status,user_edited,supersedes_fact_id,created_at,updated_at,version)
            VALUES (:id,:userId,:resumeId,:type,:organization,:role,:statement,:startDate,:endDate,
              CAST(:skills AS jsonb),:sourcePage,:sourceStart,:sourceEnd,:status,:edited,:supersedes,:now,:now,0)
            """)
        .param("id", id)
        .param("userId", userId)
        .param("resumeId", input.masterResumeId())
        .param("type", input.factType().toUpperCase(Locale.ROOT))
        .param("organization", trim(input.organization()))
        .param("role", trim(input.roleTitle()))
        .param("statement", input.statement().trim())
        .param("startDate", input.startDate())
        .param("endDate", input.endDate())
        .param("skills", write(input.skills() == null ? List.of() : input.skills()))
        .param("sourcePage", input.sourcePage())
        .param("sourceStart", input.sourceStartOffset())
        .param("sourceEnd", input.sourceEndOffset())
        .param("status", status)
        .param("edited", userEdited)
        .param("supersedes", supersedes)
        .param("now", now())
        .update();
  }

  private CandidateFact requireFact(UUID userId, UUID id) {
    return jdbc.sql("SELECT * FROM candidate_facts WHERE id=:id AND user_id=:userId")
        .param("id", id)
        .param("userId", userId)
        .query((rs, row) -> mapFact(rs))
        .optional()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate fact not found"));
  }

  private CandidateFact mapFact(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new CandidateFact(
        rs.getObject("id", UUID.class),
        rs.getObject("master_resume_id", UUID.class),
        rs.getString("fact_type"),
        rs.getString("organization"),
        rs.getString("role_title"),
        rs.getString("statement"),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        readList(rs.getString("skills_json")),
        rs.getObject("source_page", Integer.class),
        rs.getObject("source_start_offset", Integer.class),
        rs.getObject("source_end_offset", Integer.class),
        rs.getString("verification_status"),
        rs.getBoolean("user_edited"),
        rs.getObject("supersedes_fact_id", UUID.class),
        rs.getObject("created_at", OffsetDateTime.class));
  }

  private MasterResume mapResume(java.sql.ResultSet rs, List<TextBlock> blocks)
      throws java.sql.SQLException {
    String extracted = rs.getString("extracted_text");
    String preview =
        extracted == null ? null : extracted.substring(0, Math.min(extracted.length(), 4000));
    return new MasterResume(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("language_code"),
        rs.getString("original_filename"),
        rs.getString("mime_type"),
        rs.getLong("size_bytes"),
        rs.getString("sha256"),
        rs.getString("extraction_status"),
        rs.getBoolean("active"),
        rs.getObject("created_at", OffsetDateTime.class),
        preview,
        blocks);
  }

  private String sha256(byte[] content) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
  }

  private String normalizedName(String requested, String filename) {
    String value = trim(requested);
    return value == null ? filename : value.substring(0, Math.min(value.length(), 160));
  }

  private String normalizedLanguage(String language) {
    String value = trim(language);
    return value == null ? "en" : value.toLowerCase(Locale.ROOT);
  }

  private String factType(String section) {
    if (section == null) return "OTHER";
    String value = section.toUpperCase(Locale.ROOT);
    if (value.contains("EXPERIENCE")) return "EXPERIENCE_BULLET";
    if (value.contains("EDUCATION")) return "EDUCATION";
    if (value.contains("SKILL")) return "SKILL";
    if (value.contains("PROJECT")) return "PROJECT";
    if (value.contains("CERTIF")) return "CERTIFICATION";
    if (value.contains("LANGUAGE")) return "LANGUAGE";
    return "OTHER";
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Invalid candidate fact", exception);
    }
  }

  private List<String> readList(String value) {
    try {
      return value == null ? List.of() : json.readValue(value, STRING_LIST);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored candidate fact is invalid", exception);
    }
  }

  private List<SeedFact> seedFacts() {
    return List.of(
        new SeedFact(
            "EDUCATION",
            null,
            null,
            "Master's Degree in Software Engineering, Ecole Superieure Vinci (2024).",
            List.of("Software Engineering")),
        new SeedFact(
            "EDUCATION",
            null,
            null,
            "Bachelor in Computer Science, Ecole Superieure Vinci (2022).",
            List.of("Computer Science")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "Scale AI",
            "AI Training and Prompt Engineering",
            "Supported AI model training and prompt engineering.",
            List.of("Prompt engineering", "AI training")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "Scale AI",
            "AI Training and Prompt Engineering",
            "Optimized and debugged code generated by large language models.",
            List.of("Java", "JavaScript", "Python", "SQL", "LLM evaluation")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "Altados by Niji",
            "Full Stack Developer",
            "Developed responsive authenticated applications with Angular, PrimeNG, Java, Spring Boot, Spring Security, and JPA.",
            List.of("Angular", "PrimeNG", "Java", "Spring Boot")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "Flomad and Lab",
            "AI Research and Development Team Lead Intern",
            "Led AI application research and development using Spring Boot, Angular, LangChain, Gemini, and GPT integrations.",
            List.of("AI R&D", "LangChain", "Spring Boot", "Angular")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "DXC Technology",
            "Full Stack Developer Intern",
            "Built Spring Boot and Angular collaboration-product modules and optimized UI performance.",
            List.of("Spring Boot", "Angular")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "Amazon",
            "Service Investigation Cap Agent",
            "Investigated complex service issues using data collection, analysis, communication, and problem solving.",
            List.of("Data analysis", "Problem solving")),
        new SeedFact(
            "EXPERIENCE_BULLET",
            "Tamwilcom",
            "Full Stack Developer Intern",
            "Developed Java risk-management, reporting, GLPI integration, and security-incident processing capabilities.",
            List.of("Java", "Reporting", "GLPI")),
        new SeedFact(
            "SKILL",
            null,
            null,
            "Worked with RAG applications, Spring AI, Spring Data JPA, Spring Security, and WebSocket.",
            List.of("RAG", "Spring AI", "Spring Data JPA", "Spring Security", "WebSocket")),
        new SeedFact(
            "CERTIFICATION",
            null,
            null,
            "IBM AI Engineering certification.",
            List.of("AI Engineering")),
        new SeedFact(
            "CERTIFICATION",
            null,
            null,
            "Oracle Cloud Infrastructure 2023 AI Certified Foundations Associate.",
            List.of("Oracle Cloud", "AI")));
  }

  private record SeedFact(
      String type, String organization, String role, String statement, List<String> skills) {}

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
