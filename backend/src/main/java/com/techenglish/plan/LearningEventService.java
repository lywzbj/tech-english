package com.techenglish.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LearningEventService {

    private final LearningEventRepository repository;
    private final ObjectMapper objectMapper;

    public void record(Long userId, String eventType, String entityType, Long entityId, Map<String, Object> payload) {
        LearningEvent event = new LearningEvent();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setOccurredAt(Instant.now());
        if (payload != null && !payload.isEmpty()) {
            try {
                event.setPayloadJson(objectMapper.writeValueAsString(payload));
            } catch (Exception ignored) {
                // Analytics payload is best-effort.
            }
        }
        repository.save(event);
    }
}
