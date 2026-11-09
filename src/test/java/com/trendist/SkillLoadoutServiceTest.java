package com.trendist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.common.exception.ApiException;
import com.trendist.skill.service.SkillLoadoutService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 스킬 장착 저장(4.33): 칸은 actions의 SKILL_SLOT 동작이고, 고른 적 없으면 null, 빈 칸은 null 행으로 저장한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SkillLoadoutServiceTest {
    @Autowired MockMvc mvc;
    @Autowired SkillLoadoutService loadouts;
    @Autowired JdbcTemplate jdbc;

    private long newPlayer(String nickname) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}")).andExpect(status().isOk());
        return jdbc.queryForObject("select max(id) from players", Long.class);
    }

    @Test
    void slotCountIsTheNumberOfSkillSlotActions() {
        assertThat(loadouts.slotCount()).isEqualTo(4);
    }

    @Test
    void neverChosenIsNullAndSavedSlotsComeBackInSlotOrderWithEmptySlotsAsNull() throws Exception {
        long p = newPlayer("sl1");
        assertThat(loadouts.load(p)).isNull();
        loadouts.save(p, Arrays.asList("hook", null, "shield", null));
        assertThat(loadouts.load(p)).containsExactly("hook", null, "shield", null);
        // 같은 스킬을 칸끼리 옮겨도 된다
        loadouts.save(p, Arrays.asList("shield", "hook", null, null));
        assertThat(loadouts.load(p)).containsExactly("shield", "hook", null, null);
        // 전부 비워도 '고른 적 없음'과 구분된다(자동으로 다시 채워지지 않는다)
        loadouts.save(p, Arrays.asList(null, null, null, null));
        assertThat(loadouts.load(p)).containsExactly(null, null, null, null);
    }

    @Test
    void wrongSlotCountOrUnknownSkillIsRejectedAndKeepsTheOldValue() throws Exception {
        long p = newPlayer("sl2");
        loadouts.save(p, Arrays.asList("hook", null, null, null));
        assertThatThrownBy(() -> loadouts.save(p, Arrays.asList("hook", null, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> loadouts.save(p, Arrays.asList("nope", null, null, null))).isInstanceOf(ApiException.class);
        assertThat(loadouts.load(p)).containsExactly("hook", null, null, null);
    }

    @Test
    void loadoutsBelongToThePlayer() throws Exception {
        long a = newPlayer("sl3a"), b = newPlayer("sl3b");
        loadouts.save(a, Arrays.asList("hook", null, null, null));
        assertThat(loadouts.load(b)).isNull();
    }
}
