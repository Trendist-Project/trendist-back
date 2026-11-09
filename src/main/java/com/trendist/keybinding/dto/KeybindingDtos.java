package com.trendist.keybinding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 키 바인딩 API(4.33). key는 KeyboardEvent.code(null이면 비어 있음). */
public final class KeybindingDtos {
    private KeybindingDtos() {}

    /** 동작 하나의 지금 키. usingDefault가 true면 사용자가 바꾸지 않은 기본 키다 */
    public record Binding(String action, String type, Integer param, String key, boolean usingDefault) {}

    /** 쓸 수 있는 키(분류, 막은 키 여부, 같은 키로 보는 짝) */
    public record KeyInfo(String code, String label, String category, boolean reserved, String aliasOf) {}

    public record Response(List<Binding> bindings, List<KeyInfo> keys) {}

    /** key가 null이면 그 동작의 키를 비운다. 기본 키로 되돌리려면 DELETE를 쓴다 */
    public record Change(@NotBlank @Size(max = 40) String action, @Size(max = 32) String key) {}

    public record UpdateRequest(@NotNull @Size(min = 1, max = 50) List<@Valid Change> bindings) {}
}
