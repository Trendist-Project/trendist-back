package com.trendist.integration.service;

import com.trendist.common.exception.ApiException;
import com.trendist.integration.dto.IntegrationDtos;
import com.trendist.integration.entity.IntegrationLink;
import com.trendist.integration.repository.IntegrationLinkRepository;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {
    private static final int MAX_URL = 500;
    private final IntegrationLinkRepository links;

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationDtos.Item> list() {
        return links.findAllByOrderBySortOrderAsc().stream().map(IntegrationServiceImpl::item).toList();
    }

    @Override
    @Transactional
    public IntegrationDtos.Item updateUrl(String code, String rawUrl) {
        IntegrationLink l = links.findByCode(code).orElseThrow(() -> ApiException.notFound("INTEGRATION_NOT_FOUND", "없는 연동 항목이에요"));
        l.changeUrl(validUrl(rawUrl));
        links.flush();
        return item(l);
    }

    /** 앞뒤를 다듬고 http(s) 주소만 받는다(javascript: 같은 것을 막는다) */
    static String validUrl(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) throw ApiException.badRequest("INTEGRATION_URL", "주소를 입력해 주세요");
        if (s.length() > MAX_URL) throw ApiException.badRequest("INTEGRATION_URL", "주소는 " + MAX_URL + "자까지예요");
        try {
            URI u = new URI(s);
            String scheme = u.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || u.getHost() == null)
                throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.badRequest("INTEGRATION_URL", "http:// 또는 https://로 시작하는 올바른 주소를 입력해 주세요");
        }
        return s;
    }

    private static IntegrationDtos.Item item(IntegrationLink l) { return new IntegrationDtos.Item(l.getCode(), l.getLabel(), l.getUrl()); }
}
