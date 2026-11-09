package com.trendist.skill.service;

import com.trendist.common.exception.ApiException;
import com.trendist.keybinding.entity.GameAction;
import com.trendist.keybinding.repository.GameActionRepository;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.skill.entity.PlayerSkillSlot;
import com.trendist.skill.entity.Skill;
import com.trendist.skill.repository.PlayerSkillSlotRepository;
import com.trendist.skill.repository.SkillRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SkillLoadoutServiceImpl implements SkillLoadoutService {
    private final GameActionRepository actions;
    private final PlayerSkillSlotRepository slots;
    private final PlayerRepository players;
    private final SkillRepository skills;

    @Override
    @Transactional(readOnly = true)
    public int slotCount() { return actions.findSkillSlotActions().size(); }

    @Override
    @Transactional(readOnly = true)
    public List<String> load(long playerId) {
        List<PlayerSkillSlot> rows = slots.findAllByPlayerId(playerId);
        if (rows.isEmpty()) return null;
        Map<Long, String> byAction = new HashMap<>();
        for (PlayerSkillSlot r : rows) byAction.put(r.getAction().getId(), r.getSkill() == null ? null : r.getSkill().getCode());
        List<String> out = new ArrayList<>();
        for (GameAction a : actions.findSkillSlotActions()) out.add(byAction.get(a.getId()));
        return out;
    }

    @Override
    @Transactional
    public void save(long playerId, List<String> codes) {
        List<GameAction> slotActions = actions.findSkillSlotActions();
        if (codes == null || codes.size() != slotActions.size()) throw ApiException.badRequest("BAD_LOADOUT", "스킬 칸 수가 맞지 않아요");
        var player = players.findById(playerId).orElseThrow(() -> ApiException.notFound("NO_PLAYER", "플레이어를 찾을 수 없어요"));
        slots.deleteAllByPlayerId(playerId); // 같은 스킬을 칸끼리 옮겨도 유일 제약에 걸리지 않게 옛 행을 먼저 지운다
        for (int i = 0; i < slotActions.size(); i++) {
            String code = codes.get(i);
            Skill skill = code == null || code.isBlank() ? null
                    : skills.findByCode(code).orElseThrow(() -> ApiException.badRequest("UNKNOWN_SKILL", "알 수 없는 스킬이에요"));
            slots.save(new PlayerSkillSlot(player, slotActions.get(i), skill));
        }
    }
}
