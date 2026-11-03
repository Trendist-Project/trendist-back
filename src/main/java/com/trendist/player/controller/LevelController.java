package com.trendist.player.controller;

import com.trendist.player.service.LevelService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
public class LevelController {
    private final LevelService levels;

    /** 레벨 표시 옆 성장 아이콘의 바탕(4.9): 레벨별 색·진하기·무지개 여부. DB(levels)에서 읽는다. */
    @GetMapping("/badges")
    public List<LevelService.Badge> badges() {
        return levels.badges();
    }
}
