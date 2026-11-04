package com.trendist.guide.service;

import com.trendist.guide.dto.GuideDtos;
import com.trendist.common.support.Lang;
import java.util.List;

/** 게임 가이드 조회(4.17). 작성·수정은 관리자 기능에서 한다. */
public interface GuideService {
    /** 사용 중인 카테고리를 표시 순서대로. */
    default List<GuideDtos.Category> categories() { return categories(Lang.KO); }

    /** 그 언어로(영어판이 비어 있으면 한국어) */
    List<GuideDtos.Category> categories(Lang lang);

    /** 카테고리의 게시 중인 글을 표시 순서대로. 없거나 꺼진 카테고리면 404. */
    default List<GuideDtos.Article> articles(String categoryCode) { return articles(categoryCode, Lang.KO); }

    List<GuideDtos.Article> articles(String categoryCode, Lang lang);
}
