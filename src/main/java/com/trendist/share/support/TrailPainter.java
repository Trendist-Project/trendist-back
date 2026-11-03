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

    /** 입자 간격(반지름 배수, 최소 px), 수명(ms), 움직이는 동안 초당 최소 개수(0이면 반지름 간격 그대로) */
    private record Spec(double gap, double minGap, double life, double rate) {}

    private static Spec spec(String code) {
        return switch (code) {
            case "afterimage" -> new Spec(0.45, 10, 380, 0);
            case "sparkle" -> new Spec(0.3, 9, 950, 24);
            case "flame" -> new Spec(0.22, 8, 600, 40);
            case "bubble" -> new Spec(0.4, 12, 1300, 12);
            case "rainbow" -> new Spec(0.18, 6, 650, 40);
            case "footprint" -> new Spec(0.6, 14, 1700, 2.5);
            case "petal" -> new Spec(0.35, 11, 1500, 14);
            case "heart" -> new Spec(0.4, 12, 1200, 10);
            case "note" -> new Spec(0.5, 14, 1400, 8);
            case "bolt" -> new Spec(0.25, 9, 260, 18);
            case "smoke" -> new Spec(0.3, 9, 1300, 16);
            case "snow" -> new Spec(0.4, 12, 1600, 10);
            case "pawprint", "birdprint", "bearprint", "rabbitprint" -> new Spec(0.6, 14, 1700, 2.5);
            default -> null;
        };
    }

    private static final double LIFE_SCALE_R = 150, LIFE_SCALE_MAX = 3, AFTERIMAGE_SCALE_R = 90, AFTERIMAGE_SCALE_MAX = 4.5;
    private static final double MIN_SIZE_R = 18, FRAME_MS = 16;
    private static final int MAX_PARTS = 120;

    private static double lifeScale(double r, double per, double max) { return Math.min(max, 1 + r / per); }

    /** 흩날리는 속도 배수: 입자 크기의 제곱근에 비례(프론트 driftScale) */
    private static double driftScale(double s) { return Math.sqrt(s / MIN_SIZE_R); }

    /** 입자 간격: 반지름 비례, 다만 초당 rate개는 나오게 줄인다(프론트 trailGap) */
    private static double gap(Spec sp, double r, double speed) {
        double byRate = sp.rate() > 0 ? speed / sp.rate() : Double.POSITIVE_INFINITY;
        return Math.max(sp.minGap(), Math.min(r * sp.gap(), byRate));
    }

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
            double gap = gap(sp, r, moved / (FRAME_MS / 1000));
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
            if (path.size() > 90) path.removeFirst();
            return step;
        }
        if (parts.size() >= MAX_PARTS) return step;
        double life = sp.life() * lifeScale(r, LIFE_SCALE_R, LIFE_SCALE_MAX);
        double s = Math.max(r, MIN_SIZE_R), tail = r * 1.05, k = driftScale(s);
        Particle p = new Particle();
        p.born = now;
        switch (code) {
            case "sparkle" -> {
                double side = rnd(R, -0.8, 0.8) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = rnd(R, -12, 12) * k; p.vy = rnd(R, -12, 12) * k; p.size = s * rnd(R, 0.1, 0.2);
                p.life = life * rnd(R, 0.7, 1.1); p.rot = rnd(R, 0, Math.PI); p.vr = rnd(R, -2, 2); p.hue = rnd(R, 40, 60);
            }
            case "flame" -> {
                double side = rnd(R, -0.45, 0.45) * r, v = rnd(R, 30, 80);
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = (-ux * v + rnd(R, -15, 15)) * k; p.vy = (-uy * v + rnd(R, -15, 15)) * k; p.size = s * rnd(R, 0.2, 0.34);
                p.life = life * rnd(R, 0.7, 1.1); p.hue = rnd(R, 20, 45);
            }
            case "bubble" -> {
                double side = rnd(R, -0.6, 0.6) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = rnd(R, -6, 6) * k; p.vy = -rnd(R, 14, 30) * k; p.size = s * rnd(R, 0.1, 0.22);
                p.life = life * rnd(R, 0.75, 1.15); p.rot = rnd(R, 0, Math.PI * 2); p.vr = rnd(R, 2, 4);
            }
            case "footprint", "pawprint", "birdprint", "bearprint", "rabbitprint" -> {
                step ^= 1;
                double side = (step == 1 ? 1 : -1) * r * 0.32;
                p.x = x - ux * (tail + s * 0.2) + px * side; p.y = y - uy * (tail + s * 0.2) + py * side;
                p.size = s * 0.16; p.life = life; p.rot = Math.atan2(uy, ux) + Math.PI / 2;
            }
            case "petal" -> {
                double side = rnd(R, -0.7, 0.7) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = (-ux * rnd(R, 10, 30) + rnd(R, -12, 12)) * k; p.vy = (-uy * rnd(R, 10, 30) + rnd(R, 4, 20)) * k;
                p.size = s * rnd(R, 0.13, 0.2); p.life = life * rnd(R, 0.8, 1.1); p.rot = rnd(R, 0, Math.PI * 2);
                p.vr = rnd(R, -3, 3); p.hue = rnd(R, 330, 355);
            }
            case "heart" -> {
                double side = rnd(R, -0.7, 0.7) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = rnd(R, -8, 8) * k; p.vy = -rnd(R, 16, 32) * k; p.size = s * rnd(R, 0.12, 0.2);
                p.life = life * rnd(R, 0.8, 1.1); p.rot = rnd(R, -0.4, 0.4); p.hue = rnd(R, 335, 365);
            }
            case "note" -> {
                double side = rnd(R, -0.7, 0.7) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = -ux * rnd(R, 4, 12) * k; p.vy = -rnd(R, 14, 26) * k; p.size = s * rnd(R, 0.13, 0.2);
                p.life = life * rnd(R, 0.8, 1.1); p.rot = rnd(R, -0.3, 0.3); p.vr = 0; p.hue = rnd(R, 0, 360);
            }
            case "bolt" -> {
                double side = rnd(R, -0.6, 0.6) * r, back = tail - r * 0.05;
                p.x = x - ux * back + px * side; p.y = y - uy * back + py * side;
                p.size = s * rnd(R, 0.35, 0.6); p.life = life * rnd(R, 0.7, 1.2);
                p.rot = Math.atan2(-uy, -ux) + rnd(R, -1, 1); p.hue = R.nextDouble();
            }
            case "smoke" -> {
                double side = rnd(R, -0.5, 0.5) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = (-ux * rnd(R, 6, 16) + rnd(R, -6, 6)) * k; p.vy = (-uy * rnd(R, 6, 16) - rnd(R, 4, 12)) * k;
                p.size = s * rnd(R, 0.16, 0.28); p.life = life * rnd(R, 0.8, 1.15); p.rot = rnd(R, 0, Math.PI * 2);
                p.vr = rnd(R, -0.6, 0.6); p.hue = rnd(R, 205, 225);
            }
            case "snow" -> {
                double side = rnd(R, -0.8, 0.8) * r;
                p.x = x - ux * tail + px * side; p.y = y - uy * tail + py * side;
                p.vx = rnd(R, -6, 6) * k; p.vy = rnd(R, 8, 20) * k; p.size = s * rnd(R, 0.1, 0.17);
                p.life = life * rnd(R, 0.8, 1.15); p.rot = rnd(R, 0, Math.PI); p.vr = rnd(R, -1.2, 1.2); p.hue = rnd(R, 0, Math.PI * 2);
            }
            default -> { return step; }
        }
        parts.add(p);
        return step;
    }

    private static double age(double born, double now, double life) { return Math.min(1, Math.max(0, (now - born) / life)); }

    /** 생길 때 잠깐 커지며 나타난다(프론트 popIn) */
    private static double popIn(double a) { return a < 0.12 ? 0.4 + 0.6 * (a / 0.12) : 1; }

    /** 0~1 결정적 난수(프론트 hash와 같은 식) */
    private static double hash(double n) {
        double x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return x - Math.floor(x);
    }

    private static Path2D heart(double s) {
        Path2D h = new Path2D.Double();
        h.moveTo(0, s * 0.85);
        h.curveTo(-s * 1.15, s * 0.05, -s * 0.85, -s * 0.95, 0, -s * 0.4);
        h.curveTo(s * 0.85, -s * 0.95, s * 1.15, s * 0.05, 0, s * 0.85);
        h.closePath();
        return h;
    }

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

    /** 발자국류 한 개의 모양(프론트 trails.ts의 footprint·pawprint·birdprint·bearprint·rabbitprint와 같다). 앞쪽이 -y */
    private static void stepShape(Graphics2D g, String code, double s) {
        switch (code) {
            case "pawprint" -> {
                g.fill(new Ellipse2D.Double(-s * 0.85, s * 0.3 - s * 0.68, s * 1.7, s * 1.36));
                double[][] toes = {{-1.05, -0.5}, {-0.4, -1.1}, {0.4, -1.1}, {1.05, -0.5}};
                for (double[] t : toes) g.fill(new Ellipse2D.Double(t[0] * s - s * 0.27, t[1] * s - s * 0.34, s * 0.54, s * 0.68));
            }
            case "birdprint" -> {
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke((float) (s * 0.3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(0, s * 0.2, 0, -s * 1.6));
                g.draw(new Line2D.Double(0, s * 0.2, -s * 1.15, -s * 1.1));
                g.draw(new Line2D.Double(0, s * 0.2, s * 1.15, -s * 1.1));
                g.draw(new Line2D.Double(0, s * 0.2, 0, s * 1.0));
                g.setStroke(old);
            }
            case "bearprint" -> {
                g.fill(new Ellipse2D.Double(-s, s * 0.35 - s * 0.85, s * 2, s * 1.7));
                double[][] toes = {{-1.2, -0.45}, {-0.65, -0.95}, {0, -1.15}, {0.65, -0.95}, {1.2, -0.45}};
                for (double[] t : toes) g.fill(circle(t[0] * s, t[1] * s, s * 0.27));
            }
            case "rabbitprint" -> {
                for (double tx : new double[]{-0.55, 0.55}) g.fill(new Ellipse2D.Double(tx * s - s * 0.3, s * 0.6 - s * 0.95, s * 0.6, s * 1.9));
                for (double tx : new double[]{-0.3, 0.3}) g.fill(circle(tx * s, -s * 1.1, s * 0.26));
            }
            default -> {
                g.fill(new Ellipse2D.Double(-s * 0.7, -s, s * 1.4, s * 2));
                for (double tx : new double[]{-0.55, 0, 0.55}) g.fill(circle(tx * s, -s * 1.35, s * 0.28));
            }
        }
    }

    private static void draw(Graphics2D g, String code, List<Particle> parts, List<PathPoint> path, double now, Color body) {
        switch (code) {
            case "afterimage" -> {
                g.setColor(body);
                for (PathPoint p : path) {
                    double a = age(p.t(), now, p.life());
                    alpha(g, 0.3 * (1 - a) * (a < 0.15 ? a / 0.15 : 1));
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
                    double x = p.x + Math.sin(p.rot) * p.size * 0.6, s = p.size * (1 + pop * 0.6) * popIn(a);
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
                // 점마다 양옆 폭을 구해 사다리꼴 조각으로 칠한다(굵은 띠의 이음매가 겹쳐 얼룩지지 않게, 프론트와 같음)
                int n = path.size();
                double[][] e = new double[n][];
                for (int i = 0; i < n; i++) {
                    PathPoint p = path.get(i), a0 = path.get(Math.max(0, i - 1)), b0 = path.get(Math.min(n - 1, i + 1));
                    double dx = b0.x() - a0.x(), dy = b0.y() - a0.y(), len = Math.hypot(dx, dy);
                    if (len == 0) len = 1;
                    double w = p.r() * 0.7 * (1 - age(p.t(), now, p.life()) * 0.8) / 2;
                    e[i] = new double[]{p.x() - dy / len * w, p.y() + dx / len * w, p.x() + dy / len * w, p.y() - dx / len * w};
                }
                for (int i = 1; i < n; i++) {
                    PathPoint p1 = path.get(i);
                    double a = age(p1.t(), now, p1.life());
                    alpha(g, 0.6 * (1 - a));
                    g.setColor(hsl((p1.t() / 4) % 360, 90, 62));
                    Path2D q = new Path2D.Double();
                    q.moveTo(e[i - 1][0], e[i - 1][1]); q.lineTo(e[i][0], e[i][1]); q.lineTo(e[i][2], e[i][3]); q.lineTo(e[i - 1][2], e[i - 1][3]);
                    q.closePath();
                    g.fill(q);
                }
            }
            case "footprint", "pawprint", "birdprint", "bearprint", "rabbitprint" -> {
                g.setColor(new Color(10, 20, 15, 140));
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    alpha(g, a < 0.1 ? a / 0.1 : 1 - (a - 0.1) / 0.9);
                    AffineTransform old = g.getTransform();
                    g.translate(p.x, p.y); g.rotate(p.rot);
                    stepShape(g, code, p.size);
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
            case "heart" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double beat = 1 + 0.18 * Math.max(0, Math.sin((now - p.born) / 1000 * Math.PI * 3));
                    double s = p.size * popIn(a) * beat;
                    alpha(g, 0.9 * (1 - a * a));
                    AffineTransform old = g.getTransform();
                    g.translate(p.x + Math.sin((now - p.born) / 260) * p.size * 0.4, p.y); g.rotate(p.rot);
                    g.setColor(hsl(p.hue % 360, 90, 66 + 8 * a));
                    g.fill(heart(s));
                    g.setColor(new Color(255, 255, 255, 140));
                    AffineTransform o2 = g.getTransform();
                    g.translate(-s * 0.42, -s * 0.28); g.rotate(-0.6);
                    g.fill(new Ellipse2D.Double(-s * 0.2, -s * 0.12, s * 0.4, s * 0.24));
                    g.setTransform(o2);
                    g.setTransform(old);
                }
            }
            case "note" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double s = p.size * popIn(a);
                    alpha(g, 0.95 * (1 - a * a));
                    double sway = Math.sin((now - p.born) / 230 + p.hue) * p.size * 0.5;
                    g.setColor(hsl(p.hue, 80, 72));
                    AffineTransform old = g.getTransform();
                    g.translate(p.x + sway, p.y); g.rotate(p.rot + Math.sin((now - p.born) / 300) * 0.15);
                    boolean dbl = p.hue > 180;
                    g.setStroke(new BasicStroke((float) Math.max(1, s * 0.16), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (double hx : dbl ? new double[]{-s * 0.55, s * 0.55} : new double[]{0}) {
                        AffineTransform o2 = g.getTransform();
                        g.translate(hx - s * 0.18, s * 0.55); g.rotate(-0.45);
                        g.fill(new Ellipse2D.Double(-s * 0.36, -s * 0.26, s * 0.72, s * 0.52));
                        g.setTransform(o2);
                        g.draw(new Line2D.Double(hx + s * 0.14, s * 0.5, hx + s * 0.14, -s * 0.85));
                    }
                    if (dbl) {
                        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.26), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.draw(new Line2D.Double(-s * 0.41, -s * 0.85, s * 0.69, -s * 0.85));
                    } else {
                        g.draw(new QuadCurve2D.Double(s * 0.14, -s * 0.85, s * 0.7, -s * 0.5, s * 0.5, -s * 0.05));
                    }
                    g.setTransform(old);
                }
            }
            case "bolt" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double seed = p.hue * 1000 + Math.floor((now - p.born) / 50);
                    int segs = 5;
                    double len = p.size * (0.6 + 0.4 * (1 - a)), cos = Math.cos(p.rot), sin = Math.sin(p.rot);
                    Path2D line = new Path2D.Double();
                    line.moveTo(p.x, p.y);
                    for (int i = 1; i <= segs; i++) {
                        double along = len * i / segs, off = (hash(seed + i) - 0.5) * p.size * 0.45 * (i < segs ? 1 : 0.3);
                        line.lineTo(p.x + cos * along - sin * off, p.y + sin * along + cos * off);
                    }
                    double flicker = 0.55 + 0.45 * hash(seed + 99);
                    alpha(g, 0.35 * (1 - a) * flicker);
                    g.setColor(new Color(90, 200, 255));
                    g.setStroke(new BasicStroke((float) Math.max(1, p.size * 0.22), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(line);
                    alpha(g, (1 - a) * flicker);
                    g.setColor(new Color(235, 250, 255));
                    g.setStroke(new BasicStroke((float) Math.max(1, p.size * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(line);
                }
            }
            case "smoke" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double s = p.size * (0.6 + 1.3 * a);
                    alpha(g, 0.32 * (1 - a) * (a < 0.1 ? a / 0.1 : 1));
                    g.setColor(hsl(p.hue, 12, 78 - 12 * a));
                    AffineTransform old = g.getTransform();
                    g.translate(p.x, p.y); g.rotate(p.rot);
                    Area puff = new Area(circle(0, 0, s));
                    puff.add(new Area(circle(s * 0.6, -s * 0.2, s * 0.62)));
                    puff.add(new Area(circle(-s * 0.55, -s * 0.15, s * 0.58)));
                    g.fill(puff);
                    g.setTransform(old);
                }
            }
            case "snow" -> {
                for (Particle p : parts) {
                    double a = age(p.born, now, p.life);
                    double s = p.size * popIn(a);
                    alpha(g, 0.95 * (1 - a * a));
                    g.setColor(new Color(235, 246, 255));
                    g.setStroke(new BasicStroke((float) Math.max(1, s * 0.14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    AffineTransform old = g.getTransform();
                    g.translate(p.x + Math.sin((now - p.born) / 400 + p.hue) * p.size * 0.6, p.y); g.rotate(p.rot);
                    for (int i = 0; i < 6; i++) {
                        double th = i * Math.PI / 3, c = Math.cos(th), sn = Math.sin(th);
                        g.draw(new Line2D.Double(0, 0, c * s, sn * s));
                        double bx = c * s * 0.6, by = sn * s * 0.6;
                        for (double d : new double[]{-0.6, 0.6}) {
                            g.draw(new Line2D.Double(bx, by, bx + Math.cos(th + d) * s * 0.3, by + Math.sin(th + d) * s * 0.3));
                        }
                    }
                    g.setTransform(old);
                }
            }
            default -> { }
        }
    }
}
