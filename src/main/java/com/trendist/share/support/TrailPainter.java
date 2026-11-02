package com.trendist.share.support;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 이동 효과(4.23)를 카드 한 장에 멈춘 모습으로 그린다. 프론트 trails.ts의 규칙(움직인 거리마다 입자, 반지름 비례 크기·수명,
 * 원 뒤쪽 가장자리에서 나옴)을 그대로 옮겼다. 무작위 값은 효과 코드로 씨앗을 정해서 같은 카드는 늘 같은 그림이다.
 */
final class TrailPainter {
    private TrailPainter() {}

    private record Spec(double gap, double minGap, double life) {}

    private static Spec spec(String code) {
        return switch (code) {
            case "afterimage" -> new Spec(0.45, 10, 380);
            case "sparkle" -> new Spec(0.3, 9, 950);
            case "flame" -> new Spec(0.22, 8, 600);
            case "bubble" -> new Spec(0.4, 12, 1300);
            case "rainbow" -> new Spec(0.18, 6, 650);
            case "footprint" -> new Spec(0.6, 14, 1700);
            case "petal" -> new Spec(0.35, 11, 1500);
            default -> null;
        };
    }

    private static final double LIFE_SCALE_R = 150, LIFE_SCALE_MAX = 3, AFTERIMAGE_SCALE_R = 90, AFTERIMAGE_SCALE_MAX = 4.5;
    private static final int MAX_PARTS = 90;

    private static double lifeScale(double r, double per, double max) { return Math.min(max, 1 + r / per); }

    private static final class Particle {
        double x, y, vx, vy, size, born, life, rot, vr, hue;
    }

    private record PathPoint(double x, double y, double r, double t, double life) {}

    static boolean known(String code) { return code != null && spec(code) != null; }

    /** 원의 마지막 머리 방향(라디안, 0이면 위). 효과가 없으면 0.6 */
    static double heading(double cx, double cy) {
        double[] a = pos(cx, cy, 0.97), b = pos(cx, cy, 1);
        return Math.atan2(b[1] - a[1], b[0] - a[0]) + Math.PI / 2;
    }

    /** 원이 왼쪽 아래에서 곡선으로 올라와 (cx, cy)에 멈추는 길 */
    private static double[] pos(double cx, double cy, double t) {
        return new double[]{cx - 330 * (1 - t) - 40 * Math.sin(t * Math.PI), cy + 120 * (1 - t) * (1 - t)};
    }

    /** 70프레임(16ms 간격) 동안 움직였다고 보고 마지막 순간에 남아 있는 입자를 그린다 */
    static void paint(Graphics2D g, String code, double cx, double cy, double r, Color body) {
        Spec sp = spec(code);
        if (sp == null) return;
        Random rnd = new Random(code.hashCode());
        List<Particle> parts = new ArrayList<>();
        List<PathPoint> path = new ArrayList<>();
        int frames = 70;
        double start = 1000, now = start + frames * 16;
        double[] last = pos(cx, cy, 0);
        double dist = 0;
        int step = 0;
        for (int i = 1; i <= frames; i++) {
            double t = (double) i / frames, f = start + i * 16;
            double[] p = pos(cx, cy, t);
            double dx = p[0] - last[0], dy = p[1] - last[1], moved = Math.hypot(dx, dy);
            if (moved < 0.01) continue;
            double gap = Math.max(sp.minGap(), r * sp.gap());
            dist += moved;
            double ux = dx / moved, uy = dy / moved;
            int made = 0;
            while (dist >= gap && made < 4) {
                dist -= gap;
                double ex = p[0] - ux * dist, ey = p[1] - uy * dist;
                step = emit(code, sp, rnd, parts, path, ex, ey, ux, uy, r, f, step);
                made++;
            }
            if (made == 4) dist = 0;
            last = p;
        }
        // 그동안 움직인 만큼 입자 위치를 옮긴다(프론트는 프레임마다 v × dt)
        for (Particle p : parts) {
            double age = (now - p.born) / 1000.0;
            p.x += p.vx * age; p.y += p.vy * age; p.rot += p.vr * age;
        }
        parts.removeIf(p -> now - p.born >= p.life);
        path.removeIf(p -> now - p.t() > p.life());
        Graphics2D c = (Graphics2D) g.create();
        try {
            draw(c, code, parts, path, now, body);
        } finally {
            c.dispose();
        }
    }

    private static double rnd(Random r, double a, double b) { return a + r.nextDouble() * (b - a); }

    private static int emit(String code, Spec sp, Random R, List<Particle> parts, List<PathPoint> path,
                            double x, double y, double ux, double uy, double r, double now, int step) {
        double px = -uy, py = ux;
        if (code.equals("afterimage")) {
            path.add(new PathPoint(x, y, r, now, sp.life() * lifeScale(r, AFTERIMAGE_SCALE_R, AFTERIMAGE_SCALE_MAX)));
            if (path.size() > 40) path.removeFirst();
            return step;
        }
        if (code.equals("rainbow")) {
            path.add(new PathPoint(x - ux * r * 0.92, y - uy * r * 0.92, r, now, sp.life() * lifeScale(r, LIFE_SCALE_R, LIFE_SCALE_MAX)));
            if (path.size() > 60) path.removeFirst();
            return step;
        }
        if (parts.size() >= MAX_PARTS) return step;
        double life = sp.life() * lifeScale(r, LIFE_SCALE_R, LIFE_SCALE_MAX);
        double s = Math.max(r, 18), tail = r * 1.05;
        Particle p = new Particle();
        p.born = now;
        switch (code) {
            case "sparkle" -> {
                double side = rnd(R, -0.8, 0.8) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = rnd(R, -12, 12); p.vy = rnd(R, -12, 12); p.size = s * rnd(R, 0.1, 0.2);
                p.life = life * rnd(R, 0.7, 1.1); p.rot = rnd(R, 0, Math.PI); p.vr = rnd(R, -2, 2); p.hue = rnd(R, 40, 60);
            }
            case "flame" -> {
                double side = rnd(R, -0.45, 0.45) * r, v = rnd(R, 30, 80);
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = -ux * v + rnd(R, -15, 15); p.vy = -uy * v + rnd(R, -15, 15); p.size = s * rnd(R, 0.2, 0.34);
                p.life = life * rnd(R, 0.7, 1.1); p.hue = rnd(R, 20, 45);
            }
            case "bubble" -> {
                double side = rnd(R, -0.6, 0.6) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = rnd(R, -6, 6); p.vy = -rnd(R, 14, 30); p.size = s * rnd(R, 0.1, 0.22);
                p.life = life * rnd(R, 0.75, 1.15); p.rot = rnd(R, 0, Math.PI * 2); p.vr = rnd(R, 2, 4);
            }
            case "footprint" -> {
                step ^= 1;
                double side = (step == 1 ? 1 : -1) * r * 0.32;
                p.x = x - ux * (tail + s * 0.2) + px * side; p.y = y - uy * (tail + s * 0.2) + py * side;
                p.size = s * 0.16; p.life = life; p.rot = Math.atan2(uy, ux) + Math.PI / 2;
            }
            case "petal" -> {
                double side = rnd(R, -0.7, 0.7) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = -ux * rnd(R, 10, 30) + rnd(R, -12, 12); p.vy = -uy * rnd(R, 10, 30) + rnd(R, 4, 20);
                p.size = s * rnd(R, 0.13, 0.2); p.life = life * rnd(R, 0.8, 1.1); p.rot = rnd(R, 0, Math.PI * 2);
                p.vr = rnd(R, -3, 3); p.hue = rnd(R, 330, 355);
            }
            default -> { return step; }
        }
        parts.add(p);
        return step;
    }

    private static double age(double born, double now, double life) { return Math.min(1, Math.max(0, (now - born) / life)); }

    private static void alpha(Graphics2D g, double a) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, a))));
    }

    /** CSS hsl(h, s%, l%) */
    static Color hsl(double h, double s, double l) {
        h = ((h % 360) + 360) % 360 / 360; s /= 100; l /= 100;
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new Color((float) hue(p, q, h + 1.0 / 3), (float) hue(p, q, h), (float) hue(p, q, h - 1.0 / 3));
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6) return p + (q - p) * 6 * t;
        if (t < 0.5) return q;
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6;
        return p;
    }

    private static Ellipse2D circle(double x, double y, double r) { return new Ellipse2D.Double(x - r, y - r, r * 2, r * 2); }

    private static void draw(Graphics2D g, String code, List<Particle> parts, List<PathPoint> path, double now, Color body) {
        switch (code) {
            case "afterimage" -> {
                g.setColor(body);
                for (PathPoint p : path) {
                    double a = age(p.t(), now, p.life());
                    alpha(g, 0.3 * (1 - a));
                    g.fill(circle(p.x(), p.y(), p.r() * (0.95 - 0.25 * a)));
                }
            }
            case "sparkle" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double twinkle = 0.6 + 0.4 * Math.sin((now - p.born) / 60 + p.rot * 3);
                    alpha(g, (1 - a) * twinkle);
                    g.setColor(hsl(p.hue, 100, 75 + 20 * (1 - a)));
                    double s = p.size * (1 - 0.4 * a);
                    Path2D star = new Path2D.Double();
                    for (int i = 0; i < 8; i++) {
                        double rr = i % 2 == 0 ? s : s * 0.3, th = i * Math.PI / 4 + p.rot;
                        double sx = p.x + Math.cos(th) * rr, sy = p.y + Math.sin(th) * rr;
                        if (i == 0) star.moveTo(sx, sy); else star.lineTo(sx, sy);
                    }
                    star.closePath();
                    g.fill(star);
                }
            }
            case "flame" -> {
                // 프론트는 겹치면 밝아지게(lighter) 더한다. 카드 한 장에서는 보통 합성으로 충분하다
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    alpha(g, 0.75 * (1 - a));
                    g.setColor(hsl(p.hue * (1 - a * 0.9), 100, 60 - 15 * a));
                    g.fill(circle(p.x, p.y, p.size * (1 - 0.7 * a)));
                }
            }
            case "bubble" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double pop = a > 0.85 ? (a - 0.85) / 0.15 : 0;
                    double x = p.x + Math.sin(p.rot) * p.size * 0.6, s = p.size * (1 + pop * 0.6);
                    alpha(g, 0.8 * (1 - pop));
                    g.setColor(new Color(160, 220, 255, 46));
                    g.fill(circle(x, p.y, s));
                    g.setColor(new Color(200, 240, 255, 242));
                    g.setStroke(new BasicStroke((float) Math.max(1, s * 0.16)));
                    g.draw(circle(x, p.y, s));
                    g.setColor(new Color(255, 255, 255, 217));
                    g.fill(circle(x - s * 0.35, p.y - s * 0.35, s * 0.22));
                }
            }
            case "rainbow" -> {
                for (int i = 1; i < path.size(); i++) {
                    PathPoint p0 = path.get(i - 1), p1 = path.get(i);
                    double a = age(p1.t(), now, p1.life());
                    alpha(g, 0.6 * (1 - a));
                    g.setColor(hsl((p1.t() / 4) % 360, 90, 62));
                    g.setStroke(new BasicStroke((float) Math.max(0.5, p1.r() * 0.7 * (1 - a * 0.8)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(p0.x(), p0.y(), p1.x(), p1.y()));
                }
            }
            case "footprint" -> {
                g.setColor(new Color(10, 20, 15, 140));
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    alpha(g, a < 0.1 ? a / 0.1 : 1 - (a - 0.1) / 0.9);
                    double s = p.size;
                    AffineTransform old = g.getTransform();
                    g.translate(p.x, p.y); g.rotate(p.rot);
                    g.fill(new Ellipse2D.Double(-s * 0.7, -s, s * 1.4, s * 2));
                    for (double tx : new double[]{-0.55, 0, 0.55}) g.fill(circle(tx * s, -s * 1.35, s * 0.28));
                    g.setTransform(old);
                }
            }
            case "petal" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    alpha(g, 0.9 * (1 - a * a));
                    g.setColor(hsl(p.hue, 85, 80 - 8 * a));
                    double flip = 0.35 + 0.65 * Math.abs(Math.cos((now - p.born) / 180 + p.rot));
                    AffineTransform old = g.getTransform();
                    g.translate(p.x, p.y); g.rotate(p.rot);
                    g.fill(new Ellipse2D.Double(-p.size * flip, -p.size * 0.6, p.size * flip * 2, p.size * 1.2));
                    g.setTransform(old);
                }
            }
            default -> { }
        }
    }
}
