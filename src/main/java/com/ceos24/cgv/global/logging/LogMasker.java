package com.ceos24.cgv.global.logging;

/** 로그에 남기는 식별용 값(paymentKey 등)을 마스킹한다. */
public final class LogMasker {

    private static final int VISIBLE_TAIL = 4;
    private static final int MIN_LENGTH_TO_SHOW_TAIL = 8;

    private LogMasker() {}

    /** 뒤 4자리만 남긴다. 예) "tgen_20261114abcd1234" → "****1234". 짧은 값은 전부 가린다. */
    public static String mask(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() < MIN_LENGTH_TO_SHOW_TAIL) {
            return "****";
        }
        return "****" + value.substring(value.length() - VISIBLE_TAIL);
    }
}
