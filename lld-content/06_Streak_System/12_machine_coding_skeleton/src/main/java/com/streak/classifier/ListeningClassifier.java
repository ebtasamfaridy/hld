package com.streak.classifier;

import com.streak.domain.ClassifiedEvent;
import com.streak.domain.RawAppEvent;
import com.streak.domain.StreakType;

import java.util.Optional;

public final class ListeningClassifier implements ActivityClassifier {
    private final int minSeconds;

    public ListeningClassifier(int minSeconds) {
        this.minSeconds = minSeconds;
    }

    @Override
    public Optional<ClassifiedEvent> classify(RawAppEvent e) {
        if (e.kind() != RawAppEvent.Kind.EPISODE_PLAYED) return Optional.empty();
        Object dur = e.metadata().get("duration_seconds");
        int sec = (dur instanceof Number n) ? n.intValue() : 0;
        if (sec < minSeconds) return Optional.empty();
        return Optional.of(new ClassifiedEvent(
                e.userId(), StreakType.LISTENING, e.occurredAt(), e.userTimezone()));
    }
}
