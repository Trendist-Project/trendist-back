package com.trendist.skill.controller;

import com.trendist.skill.dto.SkillDtos;
import com.trendist.skill.service.SkillCatalog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스킬 목록(4.33): 코드·아이콘·종류·기본 단축키·쿨타임. 이름·설명은 프론트엔드 번역 파일이 코드로 찾는다. 로그인 없이 읽는다. */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {
    private final SkillCatalog catalog;

    @GetMapping
    public List<SkillDtos.Item> list() {
        return catalog.list();
    }
}
