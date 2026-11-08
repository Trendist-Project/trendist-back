package com.trendist.skill.dto;

public final class SkillDtos {
    private SkillDtos() {}

    /** 클라이언트가 스킬 버튼·툴팁·단축키를 만드는 데 쓰는 한 줄(4.33). defaultKey는 KeyboardEvent.code. 이름·설명은 프론트엔드 번역 파일에서 코드로 찾는다. requiredLevel은 그 레벨부터 쓸 수 있다는 해금 레벨 */
    public record Item(String code, String icon, String category, String defaultKey, double cooldownSeconds, int requiredLevel) {}
}
