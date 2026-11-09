package com.trendist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 내 단축키(4.33): 바꾼 것만 저장하고 행이 없으면 기본 키, 서버가 겹침·막은 키·모르는 동작을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KeybindingApiTest {
    @Autowired MockMvc mvc;

    private String player(String nickname) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}")).andExpect(status().isOk());
        return token;
    }

    private ResultActions getKb(String token) throws Exception {
        return mvc.perform(get("/api/me/keybindings").header("Authorization", "Bearer " + token));
    }

    private ResultActions putKb(String token, String json) throws Exception {
        return mvc.perform(put("/api/me/keybindings").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String key(String action) { return "$.bindings[?(@.action=='" + action + "')].key"; }
    private static String dflt(String action) { return "$.bindings[?(@.action=='" + action + "')].usingDefault"; }

    @Test
    void everyActionStartsOnItsDefaultKeyWithoutAnyRow() throws Exception {
        String t = player("kb1");
        getKb(t).andExpect(status().isOk())
                .andExpect(jsonPath("$.bindings.length()").value(5))
                .andExpect(jsonPath(key("skill_slot_1")).value("Digit1"))
                .andExpect(jsonPath(key("skill_slot_4")).value("Digit4"))
                .andExpect(jsonPath(key("dash")).value("Space"))
                .andExpect(jsonPath(dflt("dash")).value(true))
                .andExpect(jsonPath("$.bindings[0].type").value("SKILL_SLOT"))
                .andExpect(jsonPath("$.bindings[0].param").value(1))
                // 쓸 수 있는 키 목록: 막은 키는 reserved, 숫자패드는 위쪽 숫자의 짝
                .andExpect(jsonPath("$.keys[?(@.code=='KeyQ')].reserved").value(false))
                .andExpect(jsonPath("$.keys[?(@.code=='KeyK')].reserved").value(true))
                .andExpect(jsonPath("$.keys[?(@.code=='Numpad1')].aliasOf").value("Digit1"));
    }

    @Test
    void changingAKeyStoresOnlyThatBindingAndResetGoesBackToDefault() throws Exception {
        String t = player("kb2");
        putKb(t, "{\"bindings\":[{\"action\":\"skill_slot_1\",\"key\":\"KeyQ\"}]}").andExpect(status().isOk())
                .andExpect(jsonPath(key("skill_slot_1")).value("KeyQ"))
                .andExpect(jsonPath(dflt("skill_slot_1")).value(false))
                .andExpect(jsonPath(key("skill_slot_2")).value("Digit2"))
                .andExpect(jsonPath(dflt("skill_slot_2")).value(true));
        getKb(t).andExpect(jsonPath(key("skill_slot_1")).value("KeyQ"));
        // 기본 키와 같게 바꾸면 행을 두지 않는다
        putKb(t, "{\"bindings\":[{\"action\":\"skill_slot_1\",\"key\":\"Digit1\"}]}").andExpect(status().isOk())
                .andExpect(jsonPath(dflt("skill_slot_1")).value(true));
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"KeyX\"},{\"action\":\"skill_slot_3\",\"key\":\"KeyC\"}]}").andExpect(status().isOk());
        mvc.perform(delete("/api/me/keybindings/dash").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath(key("dash")).value("Space"))
                .andExpect(jsonPath(key("skill_slot_3")).value("KeyC"));
        mvc.perform(delete("/api/me/keybindings").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath(key("skill_slot_3")).value("Digit3"))
                .andExpect(jsonPath(dflt("skill_slot_3")).value(true));
    }

    @Test
    void conflictsWithOtherBindingsDefaultsAndNumpadAliasesAreRejected() throws Exception {
        String t = player("kb3");
        // 아직 바꾸지 않은 다른 동작의 기본 키(Digit2 = 2번 칸)
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"Digit2\"}]}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KEY_CONFLICT"));
        // 숫자패드는 위쪽 숫자와 같은 키로 본다
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"Numpad2\"}]}").andExpect(status().isConflict());
        // 한 요청 안에서 같은 키를 두 동작에
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"KeyX\"},{\"action\":\"skill_slot_1\",\"key\":\"KeyX\"}]}").andExpect(status().isConflict());
        getKb(t).andExpect(jsonPath(key("dash")).value("Space")); // 거절되면 아무것도 바뀌지 않는다
    }

    @Test
    void swappingKeysInOneRequestWorksAndAnUnboundActionStaysUnbound() throws Exception {
        String t = player("kb4");
        putKb(t, "{\"bindings\":[{\"action\":\"skill_slot_1\",\"key\":\"Digit2\"},{\"action\":\"skill_slot_2\",\"key\":\"Digit1\"}]}").andExpect(status().isOk())
                .andExpect(jsonPath(key("skill_slot_1")).value("Digit2"))
                .andExpect(jsonPath(key("skill_slot_2")).value("Digit1"));
        // 키를 비우면 기본 키로 되돌아오지 않는다
        putKb(t, "{\"bindings\":[{\"action\":\"skill_slot_3\",\"key\":null}]}").andExpect(status().isOk())
                .andExpect(jsonPath(dflt("skill_slot_3")).value(false));
        getKb(t).andExpect(jsonPath(key("skill_slot_3")).value(org.hamcrest.Matchers.contains((Object) null))); // 비운 키는 null
        // 비워 둔 동작의 기본 키는 다른 동작이 쓸 수 있다
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"Digit3\"}]}").andExpect(status().isOk()).andExpect(jsonPath(key("dash")).value("Digit3"));
    }

    @Test
    void unknownActionsReservedAndUnknownKeysAndDuplicateActionsAreBadRequests() throws Exception {
        String t = player("kb5");
        putKb(t, "{\"bindings\":[{\"action\":\"nope\",\"key\":\"KeyQ\"}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_ACTION"));
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"KeyW\"}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESERVED_KEY"));
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"Enter\"}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESERVED_KEY"));
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"NotAKey\"}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_KEY"));
        putKb(t, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"KeyX\"},{\"action\":\"dash\",\"key\":\"KeyC\"}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_ACTION"));
        putKb(t, "{\"bindings\":[]}").andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/keybindings/nope").header("Authorization", "Bearer " + t)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/keybindings")).andExpect(status().isUnauthorized());
    }

    @Test
    void bindingsBelongToThePlayerAndDoNotLeakToAnotherPlayer() throws Exception {
        String a = player("kb6a"), b = player("kb6b");
        putKb(a, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"KeyX\"}]}").andExpect(status().isOk());
        getKb(a).andExpect(jsonPath(key("dash")).value("KeyX"));
        getKb(b).andExpect(jsonPath(key("dash")).value("Space"));
        // 같은 키를 다른 플레이어가 써도 된다
        putKb(b, "{\"bindings\":[{\"action\":\"dash\",\"key\":\"KeyX\"}]}").andExpect(status().isOk());
    }
}
