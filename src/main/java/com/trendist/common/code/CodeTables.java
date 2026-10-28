package com.trendist.common.code;

import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.entity.KeywordState;
import com.trendist.keyword.repository.DataSourceRepository;
import com.trendist.keyword.repository.KeywordStateRepository;
import com.trendist.user.entity.UserState;
import com.trendist.user.repository.UserStateRepository;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 코드 테이블은 이름으로 찾아 번호를 메모리에 둔다(4.7, 11.1). 시더가 먼저 채운다. */
@Component
public class CodeTables {
    public static final String USER_NORMAL = "정상";
    public static final String USER_WITHDRAWN = "탈퇴";
    public static final String KW_ACTIVE = "활성";
    public static final String KW_INACTIVE = "비활성";
    public static final String KW_BANNED = "금지";

    private final UserStateRepository userStates;
    private final KeywordStateRepository keywordStates;
    private final DataSourceRepository dataSources;
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public CodeTables(UserStateRepository userStates, KeywordStateRepository keywordStates, DataSourceRepository dataSources) {
        this.userStates = userStates;
        this.keywordStates = keywordStates;
        this.dataSources = dataSources;
    }

    public UserState userState(String name) {
        return (UserState) cache.computeIfAbsent("us:" + name,
                k -> userStates.findByName(name).orElseThrow(() -> new IllegalStateException("user state missing: " + name)));
    }

    public KeywordState keywordState(String name) {
        return (KeywordState) cache.computeIfAbsent("ks:" + name,
                k -> keywordStates.findByName(name).orElseThrow(() -> new IllegalStateException("keyword state missing: " + name)));
    }

    public Long keywordStateId(String name) { return keywordState(name).getId(); }

    /** 가중치는 관리자가 바꿀 수 있으므로 소스는 캐시하지 않고 매번 읽는다. */
    public DataSource dataSource(String name) {
        return dataSources.findByName(name).orElseThrow(() -> new IllegalStateException("data source missing: " + name));
    }

    public void clear() { cache.clear(); }
}
