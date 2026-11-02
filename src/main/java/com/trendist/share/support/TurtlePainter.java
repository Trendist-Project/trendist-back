package com.trendist.share.support;

import java.awt.*;
import java.awt.geom.*;

/**
 * 성장 아이콘(4.9) 거북이를 그린다. 프론트 turtle.ts를 그대로 옮겼다(부위가 생기는 레벨, 진하기, 좌표 모두 같다).
 * 2 등껍질 → 3 가운데 무늬 → 4 바깥 무늬 → 5 머리 → 6 눈 → 7 앞발 → 8 뒷발·꼬리 → 9 웃는 입·볼 → 10 광택
 */
final class TurtlePainter {
    private TurtlePainter() {}

    static final String BASE = "#3fb68b";
    private static final Color SHELL = Color.decode("#2a8a66"), SHELL_LINE = Color.decode("#17573f");
    private static final Color SKIN = Color.decode("#9be0b8"), SKIN_LINE = Color.decode("#3d8f67");
    private static final Color INK = Color.decode("#10261b");
    private static final int COMPLETE = 10;

    /** 테두리 두께: 꾸민 두께는 1~8단계로 반지름에 비례, null이면 기본 */
    static double borderWidth(double r, Integer step) {
        return step != null && step > 0 ? Math.max(1.5, r * 0.03 * step) : Math.max(2, r * 0.08);
    }

    private static double reveal(int level, int appear) {
        if (level < appear) return 0;
        if (appear >= COMPLETE) return 1;
        return 0.25 + 0.75 * Math.min(1, (double) (level - appear) / (COMPLETE - appear));
    }

    private static void alpha(Graphics2D g, double a) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
    }

    private static void stroke(Graphics2D g, double w) {
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    /** canvas.arc(시계 방향 라디안)를 Arc2D(반시계 방향 도)로 */
    private static Arc2D arc(double cx, double cy, double r, double from, double to) {
        return new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, -Math.toDegrees(from), -Math.toDegrees(to - from), Arc2D.OPEN);
    }

    private static void ellipse(Graphics2D g, double cx, double cy, double rx, double ry, double rot, boolean fill, boolean line) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        if (rot != 0) g.rotate(rot);
        Shape e = new Ellipse2D.Double(-rx, -ry, rx * 2, ry * 2);
        if (fill) g.fill(e);
        if (line) { g.setColor(SKIN_LINE); g.draw(e); }
        g.setTransform(old);
    }

    /** 바탕 원은 부르는 쪽이 그린다. angle: 머리 방향(라디안, 0이면 위, 시계 방향) */
    static void paint(Graphics2D g0, double x, double y, double r, long level, double angle) {
        int lv = (int) Math.min(level, COMPLETE);
        if (lv < 2) return;
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.clip(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g.translate(x, y);
            if (angle != 0) g.rotate(angle);
            g.scale(r, r);

            double shellCy = 0.1, shellR = 0.56;
            double front = reveal(lv, 7), back = reveal(lv, 8), head = reveal(lv, 5);
            stroke(g, 0.035);
            double[][] flippers = {{-0.55, -0.22, 0.24, 0.12, -0.55, front}, {0.55, -0.22, 0.24, 0.12, 0.55, front},
                    {-0.5, 0.52, 0.18, 0.1, 0.6, back}, {0.5, 0.52, 0.18, 0.1, -0.6, back}};
            for (double[] f : flippers) {
                if (f[5] <= 0) continue;
                alpha(g, f[5]);
                g.setColor(SKIN);
                ellipse(g, f[0], f[1], f[2], f[3], f[4], true, true);
            }
            if (back > 0) {
                alpha(g, back);
                Path2D tail = new Path2D.Double();
                tail.moveTo(-0.08, 0.62); tail.lineTo(0, 0.86); tail.lineTo(0.08, 0.62); tail.closePath();
                g.setColor(SKIN); g.fill(tail); g.setColor(SKIN_LINE); g.draw(tail);
            }
            if (head > 0) {
                alpha(g, head);
                g.setColor(SKIN);
                ellipse(g, 0, -0.6, 0.25, 0.23, 0, true, true);
            }

            // 등껍질
            alpha(g, reveal(lv, 2));
            Shape shell = new Ellipse2D.Double(-shellR, shellCy - shellR, shellR * 2, shellR * 2);
            g.setColor(SHELL); g.fill(shell);
            stroke(g, 0.05); g.setColor(SHELL_LINE); g.draw(shell);

            // 무늬: 가운데 육각형과 꼭짓점에서 테두리로 뻗는 선
            double hexR = 0.24;
            double[][] hex = new double[6][];
            for (int i = 0; i < 6; i++) {
                double t = -Math.PI / 2 + i * Math.PI / 3;
                hex[i] = new double[]{Math.cos(t) * hexR, shellCy + Math.sin(t) * hexR};
            }
            double center = reveal(lv, 3), outer = reveal(lv, 4);
            if (center > 0) {
                alpha(g, center);
                Path2D h = new Path2D.Double();
                for (int i = 0; i < 6; i++) { if (i == 0) h.moveTo(hex[i][0], hex[i][1]); else h.lineTo(hex[i][0], hex[i][1]); }
                h.closePath();
                g.setColor(new Color(255, 255, 255, 31)); g.fill(h);
                stroke(g, 0.04); g.setColor(SHELL_LINE); g.draw(h);
            }
            if (outer > 0) {
                alpha(g, outer);
                stroke(g, 0.035); g.setColor(SHELL_LINE);
                for (double[] p : hex) {
                    double dx = p[0], dy = p[1] - shellCy, len = Math.hypot(dx, dy);
                    g.draw(new Line2D.Double(p[0], p[1], dx / len * shellR * 0.97, shellCy + dy / len * shellR * 0.97));
                }
            }

            // 눈 → 웃는 입·볼
            double eyes = reveal(lv, 6), smile = reveal(lv, 9);
            if (eyes > 0) {
                alpha(g, eyes);
                for (double sx : new double[]{-0.1, 0.1}) {
                    g.setColor(INK); g.fill(new Ellipse2D.Double(sx - 0.055, -0.66 - 0.055, 0.11, 0.11));
                    g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(sx + 0.018 - 0.02, -0.68 - 0.02, 0.04, 0.04));
                }
            }
            if (smile > 0) {
                alpha(g, smile);
                stroke(g, 0.03); g.setColor(INK);
                g.draw(arc(0, -0.6, 0.07, 0.2 * Math.PI, 0.8 * Math.PI));
                g.setColor(new Color(255, 128, 140, 140));
                for (double sx : new double[]{-0.16, 0.16}) g.fill(new Ellipse2D.Double(sx - 0.04, -0.56 - 0.025, 0.08, 0.05));
            }
            if (lv >= COMPLETE) {
                alpha(g, 0.35);
                stroke(g, 0.05); g.setColor(Color.WHITE);
                g.draw(arc(0, shellCy, shellR * 0.8, -0.85 * Math.PI, -0.6 * Math.PI));
            }
        } finally {
            g.dispose();
        }
    }
}
