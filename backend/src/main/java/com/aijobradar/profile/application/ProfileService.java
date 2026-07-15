package com.aijobradar.profile.application;

import com.aijobradar.matching.application.MatchRecomputeQueue;
import com.aijobradar.profile.api.ProfileModels.Authorization;
import com.aijobradar.profile.api.ProfileModels.AuthorizationInput;
import com.aijobradar.profile.api.ProfileModels.Language;
import com.aijobradar.profile.api.ProfileModels.LanguageInput;
import com.aijobradar.profile.api.ProfileModels.MinimumSalary;
import com.aijobradar.profile.api.ProfileModels.Preferences;
import com.aijobradar.profile.api.ProfileModels.Profile;
import com.aijobradar.profile.api.ProfileModels.ProfileUpdate;
import com.aijobradar.profile.api.ProfileModels.WorkingHours;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProfileService {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Clock clock;
  private final MatchRecomputeQueue matchQueue;

  public ProfileService(
      JdbcClient jdbc, ObjectMapper json, Clock clock, MatchRecomputeQueue matchQueue) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.matchQueue = matchQueue;
  }

  @Transactional
  public Profile getProfile(UUID userId) {
    ensure(userId);
    return loadProfile(userId);
  }

  @Transactional
  public Profile updateProfile(UUID userId, ProfileUpdate input) {
    ensure(userId);
    jdbc.sql(
            """
            UPDATE candidate_profiles SET headline=:headline, home_country_code=:country,
              home_region=:region, current_city=:city, relocation_preference=:relocation,
              sponsorship_required=:sponsorship, updated_at=:now, version=version+1
            WHERE user_id=:userId
            """)
        .param("headline", trim(input.headline()))
        .param("country", upper(input.homeCountryCode()))
        .param("region", trim(input.homeRegion()))
        .param("city", trim(input.currentCity()))
        .param(
            "relocation",
            input.relocationPreference() == null ? "UNKNOWN" : input.relocationPreference())
        .param("sponsorship", input.sponsorshipRequired())
        .param("now", now())
        .param("userId", userId)
        .update();
    bump(userId, "PROFILE_UPDATED", null);
    return loadProfile(userId);
  }

  @Transactional
  public Preferences getPreferences(UUID userId) {
    ensure(userId);
    return loadPreferences(userId);
  }

  @Transactional
  public Preferences updatePreferences(UUID userId, Preferences input) {
    ensure(userId);
    jdbc.sql(
            """
            UPDATE candidate_preferences SET
              target_role_families_json=CAST(:roles AS jsonb), target_seniority_json=CAST(:seniority AS jsonb),
              preferred_regions_json=CAST(:regions AS jsonb), preferred_countries_json=CAST(:countries AS jsonb),
              excluded_countries_json=CAST(:excludedCountries AS jsonb), employment_types_json=CAST(:employment AS jsonb),
              workplace_modes_json=CAST(:workplace AS jsonb), minimum_salary_json=CAST(:salary AS jsonb),
              contract_allowed=:contractAllowed, freelance_allowed=:freelanceAllowed,
              annotation_work_allowed=:annotationAllowed, temporary_work_allowed=:temporaryAllowed,
              daily_digest_enabled=:digestEnabled, daily_digest_time=:digestTime,
              minimum_match_score=:score, freshness_days=:freshness,
              excluded_companies_json=CAST(:excludedCompanies AS jsonb), excluded_keywords_json=CAST(:excludedKeywords AS jsonb),
              included_keywords_json=CAST(:includedKeywords AS jsonb), working_hours_json=CAST(:workingHours AS jsonb),
              preferred_company_sizes_json=CAST(:companySizes AS jsonb), preferred_industries_json=CAST(:industries AS jsonb),
              updated_at=:now, version=version+1 WHERE user_id=:userId
            """)
        .param("roles", write(list(input.targetRoleFamilies())))
        .param("seniority", write(list(input.targetSeniority())))
        .param("regions", write(list(input.preferredRegions())))
        .param("countries", write(upperList(input.preferredCountries())))
        .param("excludedCountries", write(upperList(input.excludedCountries())))
        .param("employment", write(list(input.employmentTypes())))
        .param("workplace", write(list(input.workplaceModes())))
        .param("salary", write(input.minimumSalary()))
        .param("contractAllowed", input.contractAllowed())
        .param("freelanceAllowed", input.freelanceAllowed())
        .param("annotationAllowed", input.annotationWorkAllowed())
        .param("temporaryAllowed", input.temporaryWorkAllowed())
        .param("digestEnabled", input.dailyDigestEnabled())
        .param("digestTime", input.dailyDigestTime())
        .param("score", input.minimumMatchScore())
        .param("freshness", input.freshnessDays())
        .param("excludedCompanies", write(list(input.excludedCompanies())))
        .param("excludedKeywords", write(list(input.excludedKeywords())))
        .param("includedKeywords", write(list(input.includedKeywords())))
        .param("workingHours", write(input.workingHours()))
        .param("companySizes", write(list(input.preferredCompanySizes())))
        .param("industries", write(list(input.preferredIndustries())))
        .param("now", now())
        .param("userId", userId)
        .update();
    bump(userId, "PREFERENCES_UPDATED", null);
    return loadPreferences(userId);
  }

  @Transactional(readOnly = true)
  public List<Language> languages(UUID userId) {
    return jdbc.sql(
            """
            SELECT id, language_code, spoken_level, written_level, professional_use, verified_by_user
            FROM candidate_languages WHERE user_id=:userId ORDER BY language_code
            """)
        .param("userId", userId)
        .query(
            (rs, row) ->
                new Language(
                    rs.getObject("id", UUID.class),
                    rs.getString("language_code"),
                    rs.getString("spoken_level"),
                    rs.getString("written_level"),
                    nullableBoolean(rs, "professional_use"),
                    rs.getBoolean("verified_by_user")))
        .list();
  }

  @Transactional
  public Language saveLanguage(UUID userId, UUID id, LanguageInput input) {
    ensure(userId);
    UUID entityId = id == null ? UUID.randomUUID() : id;
    try {
      if (id == null) {
        jdbc.sql(
                """
                INSERT INTO candidate_languages(id,user_id,language_code,spoken_level,written_level,
                  professional_use,verified_by_user,created_at,updated_at,version)
                VALUES (:id,:userId,:code,:spoken,:written,:professional,true,:now,:now,0)
                """)
            .params(
                java.util.Map.of(
                    "id", entityId,
                    "userId", userId,
                    "code", input.languageCode().toLowerCase(Locale.ROOT),
                    "spoken", level(input.spokenLevel()),
                    "written", level(input.writtenLevel()),
                    "now", now()))
            .param("professional", input.professionalUse())
            .update();
      } else {
        int changed =
            jdbc.sql(
                    """
                    UPDATE candidate_languages SET language_code=:code,spoken_level=:spoken,
                      written_level=:written,professional_use=:professional,verified_by_user=true,
                      updated_at=:now,version=version+1 WHERE id=:id AND user_id=:userId
                    """)
                .param("code", input.languageCode().toLowerCase(Locale.ROOT))
                .param("spoken", level(input.spokenLevel()))
                .param("written", level(input.writtenLevel()))
                .param("professional", input.professionalUse())
                .param("now", now())
                .param("id", id)
                .param("userId", userId)
                .update();
        requireChanged(changed, "Language not found");
      }
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Language already exists");
    }
    bump(userId, "LANGUAGE_CHANGED", entityId);
    return languages(userId).stream()
        .filter(language -> language.id().equals(entityId))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void deleteLanguage(UUID userId, UUID id) {
    requireChanged(
        jdbc.sql("DELETE FROM candidate_languages WHERE id=:id AND user_id=:userId")
            .param("id", id)
            .param("userId", userId)
            .update(),
        "Language not found");
    bump(userId, "LANGUAGE_DELETED", id);
  }

  @Transactional(readOnly = true)
  public List<Authorization> authorizations(UUID userId) {
    return jdbc.sql(
            """
            SELECT id,country_code,authorization_status,sponsorship_needed,expires_at,
              verified_by_user,notes FROM candidate_authorizations
            WHERE user_id=:userId ORDER BY country_code
            """)
        .param("userId", userId)
        .query(
            (rs, row) ->
                new Authorization(
                    rs.getObject("id", UUID.class),
                    rs.getString("country_code"),
                    rs.getString("authorization_status"),
                    nullableBoolean(rs, "sponsorship_needed"),
                    rs.getObject("expires_at", LocalDate.class),
                    rs.getBoolean("verified_by_user"),
                    rs.getString("notes")))
        .list();
  }

  @Transactional
  public Authorization saveAuthorization(UUID userId, UUID id, AuthorizationInput input) {
    ensure(userId);
    UUID entityId = id == null ? UUID.randomUUID() : id;
    try {
      if (id == null) {
        jdbc.sql(
                """
                INSERT INTO candidate_authorizations(id,user_id,country_code,authorization_status,
                  sponsorship_needed,expires_at,verified_by_user,notes,created_at,updated_at,version)
                VALUES (:id,:userId,:country,:status,:sponsorship,:expires,true,:notes,:now,:now,0)
                """)
            .param("id", entityId)
            .param("userId", userId)
            .param("country", upper(input.countryCode()))
            .param("status", authorization(input.authorizationStatus()))
            .param("sponsorship", input.sponsorshipNeeded())
            .param("expires", input.expiresAt())
            .param("notes", trim(input.notes()))
            .param("now", now())
            .update();
      } else {
        int changed =
            jdbc.sql(
                    """
                    UPDATE candidate_authorizations SET country_code=:country,authorization_status=:status,
                      sponsorship_needed=:sponsorship,expires_at=:expires,verified_by_user=true,notes=:notes,
                      updated_at=:now,version=version+1 WHERE id=:id AND user_id=:userId
                    """)
                .param("country", upper(input.countryCode()))
                .param("status", authorization(input.authorizationStatus()))
                .param("sponsorship", input.sponsorshipNeeded())
                .param("expires", input.expiresAt())
                .param("notes", trim(input.notes()))
                .param("now", now())
                .param("id", id)
                .param("userId", userId)
                .update();
        requireChanged(changed, "Authorization not found");
      }
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Authorization already exists");
    }
    bump(userId, "AUTHORIZATION_CHANGED", entityId);
    return authorizations(userId).stream()
        .filter(item -> item.id().equals(entityId))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void deleteAuthorization(UUID userId, UUID id) {
    requireChanged(
        jdbc.sql("DELETE FROM candidate_authorizations WHERE id=:id AND user_id=:userId")
            .param("id", id)
            .param("userId", userId)
            .update(),
        "Authorization not found");
    bump(userId, "AUTHORIZATION_DELETED", id);
  }

  @Transactional
  public long bump(UUID userId, String reason, UUID entityId) {
    ensure(userId);
    long version =
        jdbc.sql(
                """
                UPDATE candidate_profiles SET profile_version=profile_version+1,updated_at=:now,
                  version=version+1 WHERE user_id=:userId RETURNING profile_version
                """)
            .param("now", now())
            .param("userId", userId)
            .query(Long.class)
            .single();
    insertSnapshot(userId, version, reason, entityId);
    matchQueue.profileChanged(userId);
    return version;
  }

  private void ensure(UUID userId) {
    OffsetDateTime now = now();
    UUID profileId = UUID.randomUUID();
    int inserted =
        jdbc.sql(
                """
                INSERT INTO candidate_profiles(id,user_id,created_at,updated_at)
                VALUES (:id,:userId,:now,:now) ON CONFLICT(user_id) DO NOTHING
                """)
            .param("id", profileId)
            .param("userId", userId)
            .param("now", now)
            .update();
    jdbc.sql(
            """
            INSERT INTO candidate_preferences(id,user_id,created_at,updated_at)
            VALUES (:id,:userId,:now,:now) ON CONFLICT(user_id) DO NOTHING
            """)
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("now", now)
        .update();
    if (inserted == 1) insertSnapshot(userId, 1, "PROFILE_CREATED", profileId);
  }

  private Profile loadProfile(UUID userId) {
    return jdbc.sql(
            """
            SELECT id,headline,home_country_code,home_region,current_city,relocation_preference,
              sponsorship_required,active_master_resume_id,profile_version
            FROM candidate_profiles WHERE user_id=:userId
            """)
        .param("userId", userId)
        .query(
            (rs, row) ->
                new Profile(
                    rs.getObject("id", UUID.class),
                    rs.getString("headline"),
                    rs.getString("home_country_code"),
                    rs.getString("home_region"),
                    rs.getString("current_city"),
                    rs.getString("relocation_preference"),
                    nullableBoolean(rs, "sponsorship_required"),
                    rs.getObject("active_master_resume_id", UUID.class),
                    rs.getLong("profile_version")))
        .single();
  }

  private Preferences loadPreferences(UUID userId) {
    return jdbc.sql("SELECT * FROM candidate_preferences WHERE user_id=:userId")
        .param("userId", userId)
        .query(
            (rs, row) ->
                new Preferences(
                    readList(rs.getString("target_role_families_json")),
                    readList(rs.getString("target_seniority_json")),
                    readList(rs.getString("preferred_regions_json")),
                    readList(rs.getString("preferred_countries_json")),
                    readList(rs.getString("excluded_countries_json")),
                    readList(rs.getString("employment_types_json")),
                    readList(rs.getString("workplace_modes_json")),
                    read(rs.getString("minimum_salary_json"), MinimumSalary.class),
                    nullableBoolean(rs, "contract_allowed"),
                    nullableBoolean(rs, "freelance_allowed"),
                    nullableBoolean(rs, "annotation_work_allowed"),
                    nullableBoolean(rs, "temporary_work_allowed"),
                    rs.getBoolean("daily_digest_enabled"),
                    rs.getObject("daily_digest_time", LocalTime.class),
                    rs.getInt("minimum_match_score"),
                    rs.getInt("freshness_days"),
                    readList(rs.getString("excluded_companies_json")),
                    readList(rs.getString("excluded_keywords_json")),
                    readList(rs.getString("included_keywords_json")),
                    read(rs.getString("working_hours_json"), WorkingHours.class),
                    readList(rs.getString("preferred_company_sizes_json")),
                    readList(rs.getString("preferred_industries_json"))))
        .single();
  }

  private void insertSnapshot(UUID userId, long version, String reason, UUID entityId) {
    jdbc.sql(
            """
            INSERT INTO candidate_profile_versions(id,user_id,profile_version,reason,changed_entity_id,
              snapshot_json,created_at)
            SELECT :id,p.user_id,p.profile_version,:reason,:entityId,
              jsonb_build_object('profile',to_jsonb(p)-'user_id',
                'preferences',COALESCE(to_jsonb(cp)-'user_id','{}'::jsonb)),:now
            FROM candidate_profiles p LEFT JOIN candidate_preferences cp ON cp.user_id=p.user_id
            WHERE p.user_id=:userId AND p.profile_version=:version
            """)
        .param("id", UUID.randomUUID())
        .param("reason", reason)
        .param("entityId", entityId)
        .param("now", now())
        .param("userId", userId)
        .param("version", version)
        .update();
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Invalid profile value", exception);
    }
  }

  private List<String> readList(String value) {
    try {
      return value == null ? List.of() : json.readValue(value, STRING_LIST);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored profile data is invalid", exception);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return value == null ? null : json.readValue(value, type);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored profile data is invalid", exception);
    }
  }

  private List<String> list(List<String> value) {
    return value == null
        ? List.of()
        : value.stream().map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
  }

  private List<String> upperList(List<String> value) {
    return list(value).stream().map(item -> item.toUpperCase(Locale.ROOT)).toList();
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String upper(String value) {
    String trimmed = trim(value);
    return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
  }

  private String level(String value) {
    return value == null ? "UNKNOWN" : value;
  }

  private String authorization(String value) {
    return value == null ? "UNKNOWN" : value;
  }

  private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
    boolean value = rs.getBoolean(column);
    return rs.wasNull() ? null : value;
  }

  private void requireChanged(int changed, String message) {
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
