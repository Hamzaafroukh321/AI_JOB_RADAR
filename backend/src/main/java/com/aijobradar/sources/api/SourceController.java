package com.aijobradar.sources.api;

import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.sources.api.SourceModels.FetchRunView;
import com.aijobradar.sources.api.SourceModels.ManualImportInput;
import com.aijobradar.sources.api.SourceModels.ManualImportResult;
import com.aijobradar.sources.api.SourceModels.SourceInput;
import com.aijobradar.sources.api.SourceModels.SourceView;
import com.aijobradar.sources.application.IngestionService;
import com.aijobradar.sources.application.JobSourceConnector.ConnectorHealth;
import com.aijobradar.sources.application.SourceRegistryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {
  private final SourceRegistryService registry;
  private final IngestionService ingestion;
  private final CurrentUserService currentUser;
  private final Map<String, com.aijobradar.sources.application.JobSourceConnector> connectors;

  public SourceController(
      SourceRegistryService registry,
      IngestionService ingestion,
      CurrentUserService currentUser,
      List<com.aijobradar.sources.application.JobSourceConnector> connectors) {
    this.registry = registry;
    this.ingestion = ingestion;
    this.currentUser = currentUser;
    this.connectors =
        connectors.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    com.aijobradar.sources.application.JobSourceConnector::type,
                    java.util.function.Function.identity()));
  }

  @GetMapping
  List<SourceView> list() {
    return registry.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  SourceView create(@Valid @RequestBody SourceInput input) {
    return registry.create(input);
  }

  @PostMapping("/{id}/enable")
  SourceView enable(@PathVariable UUID id) {
    return registry.setEnabled(id, true);
  }

  @PostMapping("/{id}/disable")
  SourceView disable(@PathVariable UUID id) {
    return registry.setEnabled(id, false);
  }

  @PostMapping("/{id}/fetch")
  Map<String, UUID> fetch(@PathVariable UUID id) {
    return Map.of("fetchRunId", ingestion.fetch(id, "MANUAL"));
  }

  @GetMapping("/{id}/runs")
  List<FetchRunView> runs(@PathVariable UUID id) {
    return registry.runs(id);
  }

  @GetMapping("/{id}/health")
  ConnectorHealth health(@PathVariable UUID id) {
    SourceView source = registry.get(id);
    var connector = connectors.get(source.type());
    if (connector == null)
      return new ConnectorHealth(true, "LOCAL", "Manual imports are available");
    return connector.healthCheck(registry.configuration(id));
  }

  @PostMapping("/manual-import")
  @ResponseStatus(HttpStatus.CREATED)
  ManualImportResult manualImport(
      Authentication authentication, @Valid @RequestBody ManualImportInput input) {
    return ingestion.importManual(currentUser.require(authentication).id(), input);
  }
}
