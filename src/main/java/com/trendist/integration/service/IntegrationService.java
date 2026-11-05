package com.trendist.integration.service;

import com.trendist.integration.dto.IntegrationDtos;
import java.util.List;

public interface IntegrationService {
    List<IntegrationDtos.Item> list();

    /** 주소를 바꾼다. http(s)가 아니거나 500자를 넘거나 비면 400 */
    IntegrationDtos.Item updateUrl(String code, String url);
}
