package com.trendist.chat.service;

/** 나라 방 전체 채팅(4.13). 보내기는 게임 WebSocket으로 받고, 검사·저장은 여기서 한다. */
public interface ChatService {
    /** 저장한 메시지 번호와 시각. */
    record Saved(long id, long atMillis) {}

    /** 정리하고 검사한다: 1~100자, 링크·전화번호 불가. 통과하지 못하면 ApiException(코드 CHAT_*). */
    String validate(String raw);

    /** 검사를 통과한 메시지를 저장한다. 틱 스레드 밖(작업 스레드)에서 부른다. */
    Saved save(long countryId, long playerId, String text);
}
