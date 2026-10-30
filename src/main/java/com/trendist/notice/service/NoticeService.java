package com.trendist.notice.service;

import com.trendist.notice.dto.AdminNoticeDtos;
import com.trendist.notice.dto.NoticeItem;
import java.util.List;

/** 공지사항(4.16): 조회와 관리자 작성·수정·삭제. 팝업으로 띄우는 것은 아직 없다(설정만 저장한다). */
public interface NoticeService {
    /** 게시 중인 공지. 고정 공지 먼저, 그다음 최신순(최대 50개). */
    List<NoticeItem> list();

    /** 관리자 목록: 지우지 않은 공지 전부(고정 먼저, 최신순), 팝업 설정 포함. */
    List<AdminNoticeDtos.Item> adminList();

    /** 공지 등록. 제목 1~50자, 본문 1~5000자, 팝업 기간 1~90일(기본 7일). */
    AdminNoticeDtos.Item create(long authorUserId, AdminNoticeDtos.Request req);

    AdminNoticeDtos.Item update(long id, AdminNoticeDtos.Request req);

    /** 소프트 삭제 */
    void delete(long id);
}
