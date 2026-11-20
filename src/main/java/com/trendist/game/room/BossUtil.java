package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 바다 보스 패턴(심해 문어·전기뱀장어·복어 대왕·대형 상어·대왕 고래, 4.35 바다 보스)이 함께 쓰는 도우미: 맞을 수 있는 사람 거르기, 튕기기·끌기,
 * 화면에 보내는 모양(예고 `tele`와 연출 `fx`)의 공통 형식. 화면은 모양 종류만 알고 보스는 모른다: spots(원), sectors(부채꼴), lanes(띠), radial(방사 선), mark(표식).
 */
final class BossUtil {
    private BossUtil() {}

    /** 패턴에 맞을 수 있는 사람인가: 봇·죽은 사람·나간 사람·세이프존 안·무적 중이 아니다 */
    static boolean hittable(Room.Member m, double now) {
        return !m.bot && !m.dead && !m.offline && !m.safe && m.protectUntil <= now;
    }

    /** 방의 사람(봇·죽은·나간 사람 제외) 복사본 */
    static List<Room.Member> humans(Room room) {
        List<Room.Member> out = new ArrayList<>();
        for (Room.Member m : room.members()) if (!m.bot && !m.dead && !m.offline) out.add(m);
        return out;
    }

    /** 맞을 수 있는 사람 복사본(패턴이 도는 동안 목록이 바뀌어도 안전하다) */
    static List<Room.Member> targets(Room room, double now) {
        List<Room.Member> out = new ArrayList<>();
        for (Room.Member m : room.members()) if (hittable(m, now)) out.add(m);
        return out;
    }

    static double clampTo(Room room, double margin, double v) {
        double reach = Math.max(0, room.half() - margin);
        return Math.max(-reach, Math.min(reach, v));
    }

    /** 각도를 -π~π로 */
    static double norm(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /** (nx, ny) 방향(단위 벡터)으로 px만큼 튕겨 낸다 */
    static void push(Room room, Room.Member m, double nx, double ny, double px) {
        Room.Tuning tu = room.tuningNow();
        double strength = px / Math.max(1, tu.maxSpeed() * tu.knockbackDistance());
        room.knockFrom(m, nx, ny, strength, tu);
    }

    /** (fx, fy)에서 멀어지는 쪽으로 px만큼 튕겨 낸다 */
    static void pushAway(Room room, Room.Member m, double fx, double fy, double px) {
        double dx = m.x - fx, dy = m.y - fy, d = Math.hypot(dx, dy);
        if (d < 0.001) { dx = 1; dy = 0; d = 1; }
        push(room, m, dx / d, dy / d, px);
    }

    // ------------------------------------------------------------ 화면에 보내는 모양

    static Map<String, Object> spot(double x, double y, double r) {
        Map<String, Object> m = new HashMap<>();
        m.put("x", Math.round(x)); m.put("y", Math.round(y)); m.put("r", Math.round(r));
        return m;
    }

    /** 띠(길): 가운데 (x, y)에서 방향 dir로 길이 len, 폭 w */
    static Map<String, Object> lane(double x, double y, double dir, double len, double w) {
        Map<String, Object> m = new HashMap<>();
        m.put("x", Math.round(x)); m.put("y", Math.round(y)); m.put("dir", Math.round(dir * 1000) / 1000.0); m.put("len", Math.round(len)); m.put("w", Math.round(w));
        return m;
    }

    /** 부채꼴 한 칸(각도 a0~a1, 라디안, a0 < a1) */
    static List<Double> arc(double a0, double a1) { return List.of(Math.round(a0 * 1000) / 1000.0, Math.round(a1 * 1000) / 1000.0); }

    /** 예고(`tele`) 틀: 종류는 모두 "sea", 필요한 모양만 채워 넣는다. look은 색·분위기 */
    static Map<String, Object> tele(String look) {
        Map<String, Object> m = new HashMap<>();
        m.put("k", "sea"); m.put("look", look);
        return m;
    }

    /** 연출(`monsterFx`) 한 가지를 방 전체에 보낸다 */
    static void fx(Room room, SkillBoss b, String kind, Map<String, Object> extra) { room.broadcast(b.fx(kind, extra)); }

    /** 퍼지는 고리 연출(반지름 r까지 퍼지고 사라진다) */
    static void burst(Room room, SkillBoss b, double x, double y, double r, String look) {
        Map<String, Object> e = new HashMap<>();
        e.put("x", Math.round(x)); e.put("y", Math.round(y)); e.put("r", Math.round(r)); e.put("look", look);
        fx(room, b, "burst", e);
    }

    /** 바닥 경고 원(ms 동안 깜빡이다 사라진다) */
    static void mark(Room room, SkillBoss b, double x, double y, double r, double seconds, String look) {
        Map<String, Object> e = new HashMap<>();
        e.put("x", Math.round(x)); e.put("y", Math.round(y)); e.put("r", Math.round(r)); e.put("ms", Math.round(seconds * 1000)); e.put("look", look);
        fx(room, b, "mark", e);
    }
}
