package com.trendist.share.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class ShareDtos {
    private ShareDtos() {}

    /** 기록 한 칸(이름, 보이는 값) */
    public record Record(String name, String value) {}

    /**
     * 공유한 순간의 카드 값(4.24). share_cards.data에 JSON으로 저장하고, 이미지는 늘 이 값으로 그린다.
     * 필드 순서를 바꾸면 같은 값의 해시가 달라져 같은 카드가 새 주소로 한 번 더 생기므로 뒤에만 붙인다.
     */
    public record CardData(String nickname, long level, String title, long score, Integer rank, List<Long> scores,
                           List<String> keywords, List<Record> records, String background, String borderColor,
                           Integer borderWidth, String trail, String artPalette, String artPixels,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String lang) {
        /** lang: "en"이면 영어 카드(GLOBAL 나라). 한국어 카드는 null이라 JSON에 쓰지 않는다(기존 카드의 해시가 그대로 유지된다) */
        public boolean english() { return "en".equals(lang); }

        /** lang 없이(한국어 카드) */
        public CardData(String nickname, long level, String title, long score, Integer rank, List<Long> scores,
                        List<String> keywords, List<Record> records, String background, String borderColor,
                        Integer borderWidth, String trail, String artPalette, String artPixels) {
            this(nickname, level, title, score, rank, scores, keywords, records, background, borderColor, borderWidth, trail, artPalette, artPixels, null);
        }

        /** 픽셀 그림이 없는 카드 */
        public CardData(String nickname, long level, String title, long score, Integer rank, List<Long> scores,
                        List<String> keywords, List<Record> records, String background, String borderColor,
                        Integer borderWidth, String trail) {
            this(nickname, level, title, score, rank, scores, keywords, records, background, borderColor, borderWidth, trail, null, null, null);
        }
    }

    /**
     * 만든 공유 주소. url은 공유 페이지, imageUrl은 카드 이미지(모두 사이트 주소 기준 절대 주소).
     * totalViews·totalPlays는 내가 만든 모든 카드의 조회·[플레이 하러 가기] 클릭 합계(4.24)
     */
    public record Created(String code, String url, String imageUrl, long totalViews, long totalPlays) {}

    /** 관리자 공유 통계의 카드 한 줄(13.8). nickname은 공유한 때 이름 */
    public record AdminItem(String code, String url, String nickname, long views, long plays, java.time.Instant createdAt) {}

    /** 관리자 공유 통계(13.8) */
    public record AdminStats(long cards, long cardsToday, long views, long plays, List<AdminItem> items, long total, int page, int size) {}
}
