package com.trendist.keyword.ai;

import java.util.List;

/** RSS가 뉴스 제목을 주지 않은 키워드(사용자가 직접 등록한 유행어 등)의 관련 뉴스 제목을 찾는다(4.12, 10.4). 테스트에서는 가짜로 바꿔 끼운다. */
public interface NewsSearchClient {
    /** 키워드가 들어 있는 최근 뉴스 제목(최대 5개, 매체 이름 뺀 것). 없거나 실패하면 빈 목록. */
    List<String> search(String keyword);
}
