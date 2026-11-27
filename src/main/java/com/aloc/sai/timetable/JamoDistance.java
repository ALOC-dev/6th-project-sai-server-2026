package com.aloc.sai.timetable;

// 한글 음절을 초성·중성·종성 자모로 풀어서 편집 거리(Levenshtein)를 센다.
// OCR은 비슷한 모양의 자모를 헷갈리므로(턴→런, 닝→널) 글자 단위보다 자모 단위가 틀린 정도를 더 잘 나타낸다.
// 한글이 아닌 문자는 그 문자 하나를 한 단위로 센다.
final class JamoDistance {
    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_LAST = 0xD7A3;
    private static final int JUNG_COUNT = 21;
    private static final int JONG_COUNT = 28;
    // 자모를 음절 코드와 겹치지 않는 값으로 나타내기 위한 구간 시작값
    private static final int CHO_OFFSET = 0x110000;
    private static final int JUNG_OFFSET = CHO_OFFSET + 19;
    private static final int JONG_OFFSET = JUNG_OFFSET + JUNG_COUNT;

    private JamoDistance() {
    }

    static int[] decompose(String text) {
        return text.codePoints()
                .flatMap(cp -> {
                    if (cp < HANGUL_BASE || cp > HANGUL_LAST) {
                        return java.util.stream.IntStream.of(cp);
                    }
                    int index = cp - HANGUL_BASE;
                    int cho = index / (JUNG_COUNT * JONG_COUNT);
                    int jung = (index % (JUNG_COUNT * JONG_COUNT)) / JONG_COUNT;
                    int jong = index % JONG_COUNT;
                    return jong == 0
                            ? java.util.stream.IntStream.of(CHO_OFFSET + cho, JUNG_OFFSET + jung)
                            : java.util.stream.IntStream.of(CHO_OFFSET + cho, JUNG_OFFSET + jung, JONG_OFFSET + jong);
                })
                .toArray();
    }

    static int distance(int[] a, int[] b) {
        int[] previous = new int[b.length + 1];
        int[] current = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length; i++) {
            current[0] = i;
            for (int j = 1; j <= b.length; j++) {
                int substitution = previous[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length];
    }
}
