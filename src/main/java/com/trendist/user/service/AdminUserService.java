package com.trendist.user.service;

import com.trendist.user.dto.AdminUserDtos;

/** 관리자 사용자 관리(13.10): 찾기, 상세, 제재 걸기·풀기, 닉네임 바꾸기, 꾸미기 초기화, 관리자 권한, 강제 퇴장. */
public interface AdminUserService {
    /** filter: all / guest / google / admin / banned / withdrawn / online, sort: seen / created / score / level / plus(추가수), dir: desc(기본) / asc */
    AdminUserDtos.Page search(String countryCode, String q, String filter, String sort, String dir, int page);

    AdminUserDtos.Detail detail(long userId);

    // 나라(countryCode)를 주면 그 나라의 플레이어를 대상으로 한다(사용자는 나라마다 플레이어가 따로 있다). null이면 마지막으로 플레이한 나라.
    AdminUserDtos.Detail detail(long userId, String countryCode);

    /** 제재를 건다. 계정 금지면 갱신 토큰을 모두 폐기하고 접속 중이면 내보낸다. */
    AdminUserDtos.Detail sanction(long userId, AdminUserDtos.SanctionRequest req);

    AdminUserDtos.Detail sanction(long userId, AdminUserDtos.SanctionRequest req, String countryCode);

    /** 만료 전 제재를 푼다. 같은 종류의 다른 제재가 남아 있으면 그 끝까지 유지된다. */
    AdminUserDtos.Detail release(long sanctionId);

    AdminUserDtos.Detail release(long sanctionId, String countryCode);

    AdminUserDtos.Detail rename(long userId, String nickname);

    AdminUserDtos.Detail rename(long userId, String nickname, String countryCode);

    AdminUserDtos.Detail resetLook(long userId);

    AdminUserDtos.Detail resetLook(long userId, String countryCode);

    /** 관리자 권한 주기·빼기. 구글 계정만, 자기 자신의 권한은 뺄 수 없다. */
    AdminUserDtos.Detail setAdmin(long actorUserId, long userId, boolean admin);

    AdminUserDtos.Detail setAdmin(long actorUserId, long userId, boolean admin, String countryCode);

    void kick(long userId);

    void kick(long userId, String countryCode);
}
