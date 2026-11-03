package com.trendist.keyword.service;

import com.trendist.keyword.dto.AiCommentDtos;

/**
 * 키워드 AI 한 줄 설명(4.12, 10.4). 처음 🔥가 된 키워드에 제미나이로 설명을 만들어 저장하고 모두에게 같은 글을 보여 준다.
 * 사용자가 카드를 열 때는 AI를 부르지 않는다(DB 값만 읽음). AI 호출은 새 키워드가 생길 때와 관리자가 다시 요청할 때뿐이다.
 */
public interface AiCommentService {
    /** 설명이 없는 키워드를 한도(분당·하루) 안에서 만든다. 1분마다 부른다. 만든 개수를 돌려준다 */
    int generatePending();

    /** 사용자 갱신 요청("설명이 맞지 않아요"). 같은 사람이 같은 키워드에 열린 요청은 하나만 둔다 */
    void request(long userId, long keywordId);

    /** 관리자 목록. filter: requests(갱신 요청 있음) / missing(설명 없음) / all(설명 있음) */
    AiCommentDtos.Page list(String countryCode, String filter, int page);

    /** 관리자가 AI에게 다시 요청한다. 새 설명을 저장하고 열린 요청을 모두 닫는다 */
    AiCommentDtos.Row regenerate(long keywordId);

    /** 관리자가 설명을 직접 고친다(1~200자). 열린 요청을 모두 닫는다 */
    AiCommentDtos.Row edit(long keywordId, String comment);

    /** 설명은 그대로 두고 열린 요청만 닫는다 */
    AiCommentDtos.Row dismiss(long keywordId);

    /** 처리 전 갱신 요청 수(관리자 대시보드) */
    long openRequestCount();
}
