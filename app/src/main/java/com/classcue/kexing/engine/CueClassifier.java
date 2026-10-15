package com.classcue.kexing.engine;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight, deterministic first-pass classifier. It deliberately requires
 * contextual evidence for one-character aliases to reduce false alarms.
 */
public final class CueClassifier {
    private CueClassifier() {}

    /** Optional sound-alike matcher for names that speech recognition spells with other characters. */
    public interface NameSoundMatcher {
        /** Both arguments are already normalized; returns whether [name] is spoken somewhere in [text]. */
        boolean soundsLike(String text, String name);
    }

    enum IdentityMatch {
        NONE(0.0),
        STUDENT_ID(0.76),
        EXACT_NAME(0.72),
        SOUNDS_LIKE_NAME(0.62),
        ALIAS(0.58),
        SOUNDS_LIKE_ALIAS(0.50);

        final double weight;

        IdentityMatch(double weight) {
            this.weight = weight;
        }
    }

    public static CueResult classify(String rawText, ClassifierConfig config) {
        return classify(rawText, config, null);
    }

    public static CueResult classify(String rawText, ClassifierConfig config, NameSoundMatcher sounds) {
        String text = normalize(rawText);
        if (text.isEmpty()) return CueResult.none(0.0, "没有可分析的文字");

        boolean callContext = containsAny(text, ClassroomCueLexicon.CALL_CUES);
        boolean questionContext = looksLikeQuestion(text);
        boolean explicitQuestion = hasExplicitQuestionSignal(text);
        boolean studentDirected = callContext || containsAny(text, ClassroomCueLexicon.AUDIENCE_CUES);
        boolean customContext = containsAny(text, config.customKeywords);
        IdentityMatch identity = matchIdentity(text, config, callContext, sounds);
        boolean personMatch = identity != IdentityMatch.NONE;

        double score = identity.weight;
        if (callContext) score += 0.32;
        if (questionContext) score += 0.30;
        if (customContext) score += 0.12;
        if (customContext) score = Math.max(score, 0.78);
        if (personMatch && (callContext || questionContext)) score += 0.08;
        if (!personMatch && questionContext) {
            score = Math.max(score, studentDirected ? 0.76 : explicitQuestion ? 0.62 : 0.50);
        }
        if (!personMatch && callContext) score = Math.max(score, 0.38);
        score = Math.min(score, 0.99);

        CueType type = CueType.NONE;
        if (config.detectQuestions && personMatch && questionContext) {
            type = CueType.DIRECT_QUESTION;
        } else if (config.detectRollCall && personMatch && (callContext || !questionContext)) {
            type = CueType.ROLL_CALL;
        } else if (config.detectQuestions && config.detectGeneralQuestions && questionContext) {
            type = CueType.GENERAL_QUESTION;
        } else if (customContext) {
            type = callContext && config.detectRollCall ? CueType.ROLL_CALL : CueType.GENERAL_QUESTION;
        }

        double threshold = thresholdFor(config.sensitivity, type);
        // A user-defined strong keyword is an explicit opt-in alert rule. It must
        // not be suppressed when other words in the same utterance make the
        // result look like a roll call (whose sensitivity threshold is higher).
        // The settings UI promises that finding any strong keyword alerts.
        if (type == CueType.NONE || (!customContext && score < threshold)) {
            return CueResult.none(score, score < threshold ? "低于当前灵敏度阈值" : "当前模式不提醒此事件");
        }

        RiskLevel risk = score >= 0.86 ? RiskLevel.HIGH
                : score >= 0.70 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        String reason;
        if (customContext && !personMatch) reason = "命中自定义强提醒词";
        else if (type == CueType.DIRECT_QUESTION) reason = soundsAlike(identity)
                ? "检测到读音相近的称呼和提问语气" : "检测到称呼和提问语气";
        else if (type == CueType.ROLL_CALL) reason = soundsAlike(identity)
                ? "检测到读音相近的姓名和点名语境" : "检测到姓名/学号和点名语境";
        else reason = studentDirected ? "检测到面向学生的提问" : "检测到课堂提问语气（最灵敏档）";
        return new CueResult(type, risk, score, reason);
    }

    /** Whether the utterance names the user, with the same rules the classifier uses. */
    public static boolean mentionsIdentity(String rawText, ClassifierConfig config, NameSoundMatcher sounds) {
        String text = normalize(rawText);
        if (text.isEmpty()) return false;
        boolean callContext = containsAny(text, ClassroomCueLexicon.CALL_CUES);
        return matchIdentity(text, config, callContext, sounds) != IdentityMatch.NONE;
    }

    static IdentityMatch matchIdentity(
            String text,
            ClassifierConfig config,
            boolean callContext,
            NameSoundMatcher sounds
    ) {
        String name = normalize(config.displayName);
        if (!config.studentId.isEmpty() && text.contains(normalize(config.studentId))) {
            return IdentityMatch.STUDENT_ID;
        }
        if (tokenMatch(text, name, callContext)) return IdentityMatch.EXACT_NAME;
        for (String item : config.aliases) {
            if (tokenMatch(text, normalize(item), callContext)) return IdentityMatch.ALIAS;
        }
        if (sounds == null) return IdentityMatch.NONE;
        // Sound-alike matching only for multi-character names: one syllable matches far too much.
        if (name.length() >= 2 && sounds.soundsLike(text, name)) return IdentityMatch.SOUNDS_LIKE_NAME;
        for (String item : config.aliases) {
            String alias = normalize(item);
            if (alias.length() >= 2 && sounds.soundsLike(text, alias)) return IdentityMatch.SOUNDS_LIKE_ALIAS;
        }
        return IdentityMatch.NONE;
    }

    private static boolean soundsAlike(IdentityMatch identity) {
        return identity == IdentityMatch.SOUNDS_LIKE_NAME || identity == IdentityMatch.SOUNDS_LIKE_ALIAS;
    }

    private static double thresholdFor(int sensitivity, CueType type) {
        double[] thresholds = {0.88, 0.79, 0.68, 0.58, 0.46};
        int level = Math.max(1, Math.min(5, sensitivity));
        double base = thresholds[level - 1];
        // A whole-class question needs evidence that the teacher is addressing students
        // (score 0.76). A bare interrogative inside the lecture scores 0.62 and only alerts
        // at the most sensitive setting; vague fragments (0.50) never do.
        if (type == CueType.GENERAL_QUESTION) base = Math.max(base, level >= 5 ? 0.60 : 0.68);
        return base;
    }

    public static boolean hasExplicitQuestionSignal(String rawText) {
        String text = normalize(rawText);
        if (text.contains("?") || text.contains("？")) return true;
        String[] signals = {
                "为什么", "怎么", "如何", "是否", "有没有", "能不能",
                "可不可以", "请问", "哪位", "哪个", "哪一个", "请解释", "解释一下",
                "请说明", "说明一下", "请比较", "比较一下", "举例说明", "请回答", "回答一下"
        };
        for (String signal : signals) {
            if (text.contains(signal)) return true;
        }
        return text.endsWith("吗") || text.endsWith("呢");
    }

    /**
     * One-character names and aliases appear inside ordinary words all the time ("说明" contains
     * "明"), so they only count when the teacher is calling on someone and the character is
     * used as a form of address.
     */
    private static boolean tokenMatch(String text, String token, boolean callContext) {
        if (token.isEmpty()) return false;
        if (token.length() == 1) return callContext && addressed(text, token);
        return text.contains(token);
    }

    private static boolean addressed(String text, String token) {
        String[] suffixes = {"同学", "你", "来", "回答", "起来", "站起来"};
        String[] prefixes = {"请", "叫", "点到", "轮到"};
        for (String suffix : suffixes) {
            if (text.contains(token + suffix)) return true;
        }
        for (String prefix : prefixes) {
            if (text.contains(prefix + token)) return true;
        }
        return false;
    }

    /** Returns whether text contains a built-in classroom question/task expression. */
    public static boolean looksLikeQuestion(String rawText) {
        String text = normalize(rawText);
        return containsAny(text, ClassroomCueLexicon.QUESTION_CUES)
                || text.contains("?") || text.contains("？");
    }

    /** Returns whether a partial or final transcript contains any configured strong keyword. */
    public static boolean containsCustomKeyword(String rawText, List<String> customKeywords) {
        return containsAny(normalize(rawText), customKeywords);
    }

    /** Number of unique built-in call and question expressions. */
    public static int builtInCueCount() {
        return ClassroomCueLexicon.size();
    }

    private static boolean containsAny(String text, List<String> candidates) {
        for (String candidate : candidates) {
            String value = normalize(candidate);
            if (!value.isEmpty() && text.contains(value)) return true;
        }
        return false;
    }

    static String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！、；：,.!;:]", "")
                .trim();
    }
}
