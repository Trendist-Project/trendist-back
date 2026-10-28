package com.trendist.notice.service;

import com.trendist.notice.dto.NoticeItem;
import java.util.List;

/** 공지사항 조회(4.16). 작성·수정·팝업은 관리자 기능과 함께 붙인다. */
public interface NoticeService {
    /** 게시 중인 공지. 고정 공지 먼저, 그다음 최신순(최대 50개). */
    List<NoticeItem> list();
}
