package com.streak.classifier;

import com.streak.domain.ClassifiedEvent;
import com.streak.domain.RawAppEvent;
import com.streak.domain.StreakType;

import java.util.Optional;

public final class AppVisitClassifier implements ActivityClassifier {
    @Override
    public Optional<ClassifiedEvent> classify(RawAppEvent e) {
        if (e.kind() != RawAppEvent.Kind.SESSION_STARTED) return Optional.empty();
        Object source = e.metadata().get("source");
        if ("background_refresh".equals(source)) return Optional.empty();
        return Optional.of(new ClassifiedEvent(
                e.userId(), StreakType.APP_VISIT, e.occurredAt(), e.userTimezone()));
    }
}
