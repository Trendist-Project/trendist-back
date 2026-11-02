package com.trendist.dashboard.service;

import com.trendist.dashboard.dto.DashboardDtos;

/** 관리자 대시보드(13.11): 실시간 현황, 처리할 일, 오늘 요약, 키워드·리더보드·새 플레이어·공지·공유를 한 번에 모은다. */
public interface DashboardService {
    DashboardDtos.Dashboard dashboard(String countryCode);
}
