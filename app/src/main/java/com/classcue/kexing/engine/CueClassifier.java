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

    public static CueResult classify(String rawText, ClassifierConfig config) {
        String text = normalize(rawText);
        if (text.isEmpty()) return CueResult.none(0.0, "没有可分析的文字");

        boolean callContext = containsAny(text, ClassroomCueLexicon.CALL_CUES);
        boolean questionContext = looksLikeQuestion(text);
        boolean explicitQuestion = hasExplicitQuestionSignal(text);
        boolean customContext = containsAny(text, config.customKeywords);
        boolean exactName = tokenMatch(text, normalize(config.displayName), callContext || questionContext);
        boolean studentId = !config.studentId.isEmpty() && text.contains(normalize(config.studentId));
        boolean alias = false;
        for (String item : config.aliases) {
            if (tokenMatch(text, normalize(item), callContext || questionContext)) {
                alias = true;
                break;
            }
        }
        boolean personMatch = exactName || studentId || alias;

        double score = 0.0;
        if (exactName) score += 0.72;
        else if (studentId) score += 0.76;
        else if (alias) score += 0.58;
        if (callContext) score += 0.32;
        if (questionContext) score += 0.30;
        if (customContext) score += 0.12;
        if (customContext) score = Math.max(score, 0.78);
        if (personMatch && (callContext || questionContext)) score += 0.08;
        if (!personMatch && questionContext) score = Math.max(score, explicitQuestion ? 0.72 : 0.56);
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
        else if (type == CueType.DIRECT_QUESTION) reason = "检测到称呼和提问语气";
        else if (type == CueType.ROLL_CALL) reason = "检测到姓名/学号和点名语境";
        else reason = "检测到面向全班的问题";
        return new CueResult(type, risk, score, reason);
    }

    private static double thresholdFor(int sensitivity, CueType type) {
        double[] thresholds = {0.88, 0.79, 0.68, 0.58, 0.46};
        double base = thresholds[Math.max(1, Math.min(5, sensitivity)) - 1];
        // Generic lecture wording often contains a broad cue such as “什么” or “怎么”.
        // Keep general-class alerts behind a higher floor; explicit question signals still
        // receive the 0.72 score above, while weak ASR fragments remain below the floor.
        if (type == CueType.GENERAL_QUESTION) base = Math.max(base, 0.68);
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

    private static boolean tokenMatch(String text, String token, boolean contextual) {
        if (token.isEmpty()) return false;
        if (token.length() == 1 && !contextual) return false;
        return text.contains(token);
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
