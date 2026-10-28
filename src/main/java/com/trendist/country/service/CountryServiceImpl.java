package com.trendist.country.service;

import com.trendist.common.exception.ApiException;
import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import org.springframework.stereotype.Service;

@Service
public class CountryServiceImpl implements CountryService {
    private final CountryRepository countries;

    public CountryServiceImpl(CountryRepository countries) { this.countries = countries; }

    @Override
    public Country requireOpen(String code) {
        Country c = countries.findByCode(code == null ? DEFAULT_COUNTRY : code)
                .orElseThrow(() -> ApiException.badRequest("COUNTRY_UNKNOWN", "알 수 없는 나라예요"));
        if (!c.isEnabled()) throw ApiException.badRequest("COUNTRY_CLOSED", "아직 열지 않은 나라예요");
        return c;
    }
}
