package com.trendist.share.service;

import com.trendist.share.dto.ShareDtos;
import java.util.Optional;

/**
 * 자랑하기 카드 공유 주소(4.24). 공유하는 순간의 카드 값만 저장하고 이미지는 요청 때 그린다(그린 결과는 메모리 캐시).
 * 나중에 꾸미기·레벨·칭호가 바뀌어도 이미 만든 주소의 카드는 그때 모습 그대로다.
 */
public interface ShareCardService {
    /** 지금 값으로 카드를 저장하고 공유 주소를 준다. 같은 플레이어의 값이 모두 같은 카드가 있으면 그 주소를 준다. 분당 10번 */
    ShareDtos.Created create(long userId);

    /** 저장한 카드 값. 없는 코드면 비어 있다 */
    Optional<ShareDtos.CardData> find(String code);

    /** 카드 PNG. 없는 코드면 비어 있다 */
    Optional<byte[]> image(String code);

    /** 공유 페이지를 열거나([나도 겨뤄 보기]를 누른) 것을 센다. 같은 방문자 카드마다 하루 1번, 크롤러 제외(4.24) */
    void count(String code, com.trendist.share.support.ShareViewCounter.Kind kind, String ip, String userAgent);

    /** 관리자 공유 통계. sort: views / plays / createdAt(13.8) */
    ShareDtos.AdminStats adminStats(String sort, int page);

    /** 공유 페이지·이미지 주소의 기준이 되는 사이트 주소(끝에 / 없음) */
    String baseUrl();
}
