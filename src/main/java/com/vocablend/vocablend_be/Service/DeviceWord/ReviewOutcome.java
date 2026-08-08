package com.vocablend.vocablend_be.Service.DeviceWord;

// The wire values stay camelCase because the mobile app's AnswerOutcome type
// already uses 'gotIt' | 'stillLearning'; the Java constants keep Java naming.
public enum ReviewOutcome {

    GOT_IT("gotIt"),
    STILL_LEARNING("stillLearning");

    private final String param;

    ReviewOutcome(String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }

    public static ReviewOutcome fromParam(String value) {
        for (ReviewOutcome outcome : values()) {
            if (outcome.param.equals(value)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("Unknown review outcome: " + value);
    }
}
