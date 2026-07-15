package com.aijobradar.ai.infrastructure;

import com.aijobradar.ai.application.LanguageModelProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "radar.ai.enabled", havingValue = "false", matchIfMissing = true)
public final class DisabledLanguageModelProvider implements LanguageModelProvider {
  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public <T> AiResult<T> generateStructured(
      AiTaskType taskType,
      List<AiMessage> messages,
      String jsonSchema,
      Class<T> responseType,
      AiRequestOptions options) {
    return AiResult.disabled();
  }
}
