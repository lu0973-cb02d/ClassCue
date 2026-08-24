package com.classcue.kexing.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class ClassifierConfig {
    public final String displayName;
    public final String studentId;
    public final List<String> aliases;
    public final List<String> customKeywords;
    public final int sensitivity;
    public final boolean detectRollCall;
    public final boolean detectQuestions;
    public final boolean detectGeneralQuestions;

    public ClassifierConfig(
            String displayName,
            String studentId,
            List<String> aliases,
            List<String> customKeywords,
            int sensitivity,
            boolean detectRollCall,
            boolean detectQuestions,
            boolean detectGeneralQuestions
    ) {
        this.displayName = safe(displayName);
        this.studentId = safe(studentId);
        this.aliases = immutableCopy(aliases);
        this.customKeywords = immutableCopy(customKeywords);
        this.sensitivity = Math.max(1, Math.min(5, sensitivity));
        this.detectRollCall = detectRollCall;
        this.detectQuestions = detectQuestions;
        this.detectGeneralQuestions = detectGeneralQuestions;
    }

    private static List<String> immutableCopy(List<String> input) {
        if (input == null) return Collections.emptyList();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : input) {
            String value = safe(item);
            if (!value.isEmpty()) unique.add(value);
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static String safe(String input) {
        return input == null ? "" : input.trim();
    }
}
