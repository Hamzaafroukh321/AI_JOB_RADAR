package com.aijobradar.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobQueryServiceTest {
  private final JobQueryService service = new JobQueryService(null, null, null);

  @Test
  void personalJuniorSearchAddsRoleAndSeniorityGuardrails() {
    String where =
        service.where(search("ALL_AI", "PERSONAL_AI_SOFTWARE", "JUNIOR_ENTRY", "REMOTE"));

    assertThat(where)
        .contains("j.ai_relevance IN ('HIGH','MEDIUM')")
        .contains("lower(j.original_title) ~ :targetTitlePattern")
        .contains("lower(j.original_title) !~ :unrelatedTitlePattern")
        .contains("j.seniority NOT IN ('SENIOR','STAFF','LEAD','MANAGER')")
        .contains("lower(j.original_title) !~ :seniorTitlePattern")
        .contains("j.workplace_mode=:workplace")
        .contains("r.region_code=:section");
  }

  @Test
  void broaderSearchIsOnlyEnabledByExplicitFilterValues() {
    String where = service.where(search(null, "AI_ALL", "ALL", null));

    assertThat(where)
        .doesNotContain("targetTitlePattern")
        .doesNotContain("unrelatedTitlePattern")
        .doesNotContain("seniorTitlePattern")
        .doesNotContain("j.seniority=");
  }

  private JobQueryService.Search search(
      String section, String focus, String experience, String workplace) {
    return new JobQueryService.Search(
        null,
        section,
        null,
        null,
        workplace,
        null,
        focus,
        experience,
        false,
        false,
        false,
        "NEWEST",
        0,
        25);
  }
}
