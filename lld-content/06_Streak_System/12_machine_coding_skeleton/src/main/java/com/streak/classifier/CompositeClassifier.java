package com.streak.classifier;

import com.streak.domain.ClassifiedEvent;
import com.streak.domain.RawAppEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite: runs all classifiers and returns ALL successful classifications.
 * Used so we always track every type the user qualifies for, regardless of
 * the admin-chosen "active" type. Switching active type then becomes a
 * read-side concern only.
 */
public final class CompositeClassifier {
    private final List<ActivityClassifier> classifiers;

    public CompositeClassifier(List<ActivityClassifier> classifiers) {
        this.classifiers = List.copyOf(classifiers);
    }

    public List<ClassifiedEvent> classifyAll(RawAppEvent event) {
        List<ClassifiedEvent> results = new ArrayList<>(2);
        for (ActivityClassifier c : classifiers) {
            c.classify(event).ifPresent(results::add);
        }
        return results;
    }
}
