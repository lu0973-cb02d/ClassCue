package com.classcue.kexing.engine;

public final class CueResult {
    public final CueType type;
    public final RiskLevel risk;
    public final double confidence;
    public final String reason;

    public CueResult(CueType type, RiskLevel risk, double confidence, String reason) {
        this.type = type;
        this.risk = risk;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.reason = reason == null ? "" : reason;
    }

    public boolean shouldAlert() {
        return type != CueType.NONE;
    }

    public static CueResult none(double score, String reason) {
        return new CueResult(CueType.NONE, RiskLevel.LOW, score, reason);
    }
}
