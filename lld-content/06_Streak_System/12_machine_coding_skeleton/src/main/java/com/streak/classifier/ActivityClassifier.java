package com.streak.classifier;

import com.streak.domain.ClassifiedEvent;
import com.streak.domain.RawAppEvent;

import java.util.Optional;

/** Strategy: each implementation knows how to detect ONE streak type. */
public interface ActivityClassifier {
    Optional<ClassifiedEvent> classify(RawAppEvent event);
}
