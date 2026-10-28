package com.trendist.guide.service;

import com.trendist.guide.dto.GuideDtos;
import java.util.List;

/** 게임 가이드 조회(4.17). 작성·수정은 관리자 기능에서 한다. */
public interface GuideService {
    /** 사용 중인 카테고리를 표시 순서대로. */
    List<GuideDtos.Category> categories();

    /** 카테고리의 게시 중인 글을 표시 순서대로. 없거나 꺼진 카테고리면 404. */
    List<GuideDtos.Article> articles(String categoryCode);
}
