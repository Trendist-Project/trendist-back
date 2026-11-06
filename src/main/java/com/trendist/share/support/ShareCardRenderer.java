package com.trendist.share.support;

import com.trendist.share.dto.ShareDtos;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 자랑하기 카드(4.24) 1080×1350 PNG를 서버에서 그린다. 배치는 프론트에 있던 카드(shareCard.ts)와 같다.
 * 글꼴은 jar에 넣은 나눔스퀘어 ac(OFL)라서 서버에 한글 글꼴이 없어도 같은 모양이다. 그림은 저장한 값으로만 정해진다.
 */
@Component
public class ShareCardRenderer {
    public static final int W = 1080, H = 1350;
    private static final Color ACCENT = Color.decode("#3fb68b"), TEXT = Color.decode("#e8eef6"), MUTED = Color.decode("#8b9bb0");
    private static final Color PANEL = new Color(20, 27, 38, 217), PANEL_LINE = Color.decode("#263243");

    private final Font regular, bold, extraBold;
    private final BufferedImage logo;

    public ShareCardRenderer() {
        regular = font("NanumSquare_acR.ttf");
        bold = font("NanumSquare_acB.ttf");
        extraBold = font("NanumSquare_acEB.ttf");
        logo = image("/share/logo.png");
    }

    private static Font font(String file) {
        try (InputStream in = ShareCardRenderer.class.getResourceAsStream("/share/fonts/" + file)) {
            if (in == null) throw new IllegalStateException("글꼴이 없어요: " + file);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("글꼴을 읽지 못했어요: " + file, e);
        }
    }

    private static BufferedImage image(String path) {
        try (InputStream in = ShareCardRenderer.class.getResourceAsStream(path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** 굵기 500 → R, 600·700 → B, 800 → EB */
    private Font f(int weight, float size) {
        return (weight >= 800 ? extraBold : weight >= 600 ? bold : regular).deriveFont(size);
    }

    private enum Align { LEFT, CENTER, RIGHT }

    /** canvas의 textBaseline = 'middle'처럼 y를 글자 가운데에 맞춰 쓴다 */
    private static void text(Graphics2D g, String s, double x, double y, Align align) {
        FontMetrics m = g.getFontMetrics();
        double w = m.stringWidth(s);
        double left = switch (align) { case LEFT -> x; case CENTER -> x - w / 2; case RIGHT -> x - w; };
        g.drawString(s, (float) left, (float) (y + (m.getAscent() - m.getDescent()) / 2.0));
    }

    /** 너비를 넘으면 뒤를 …로 줄인다(글자 단위) */
    private static String fit(Graphics2D g, String s, double max) {
        FontMetrics m = g.getFontMetrics();
        if (m.stringWidth(s) <= max) return s;
        int[] cps = s.codePoints().toArray();
        int n = cps.length;
        while (n > 1 && m.stringWidth(new String(cps, 0, n) + "…") > max) n--;
        return new String(cps, 0, n) + "…";
    }

    private static Shape round(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    private static Color color(String hex, Color fallback) {
        try {
            return hex == null ? fallback : Color.decode(hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** site: 카드 아래에 적는 사이트 주소(https:// 없이) */
    public byte[] png(ShareDtos.CardData d, String site) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            draw(g, d, site);
        } finally {
            g.dispose();
        }
        try (var out = new ByteArrayOutputStream(400_000)) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void draw(Graphics2D g, ShareDtos.CardData d, String site) {
        boolean en = d.english(); // GLOBAL 카드는 영어 문구(글꼴은 같은 나눔스퀘어 ac: 라틴 글자 포함)
        // 바탕: 어두운 그라데이션 + 떠다니는 원 + 원 뒤의 빛
        g.setPaint(new GradientPaint(0, 0, Color.decode("#0d1420"), W, H, Color.decode("#122433")));
        g.fillRect(0, 0, W, H);
        double[][] blobs = {{90, 260, 120, 0.08}, {990, 180, 90, 0.07}, {960, 760, 160, 0.06}, {120, 980, 80, 0.07}, {880, 1180, 60, 0.08}};
        for (double[] b : blobs) {
            g.setColor(new Color(63, 182, 139, (int) Math.round(b[3] * 255)));
            g.fill(new Ellipse2D.Double(b[0] - b[2], b[1] - b[2], b[2] * 2, b[2] * 2));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, 345), 420, new float[]{0.095f, 1f},
                new Color[]{new Color(63, 182, 139, 46), new Color(63, 182, 139, 0)}));
        g.fillRect(0, 0, W, 900);

        // 머리: 로고 + 이름
        if (logo != null) g.drawImage(logo, 64, 52, 76, 76, null);
        g.setColor(TEXT); g.setFont(f(800, 44));
        text(g, "Trendist", logo != null ? 156 : 64, 92, Align.LEFT);
        g.setColor(MUTED); g.setFont(f(500, 28));
        text(g, en ? "My Trend Report" : "내 트렌드 성적표", W - 64, 92, Align.RIGHT);

        circle(g, d, W / 2.0, 345, 140);

        // 닉네임·칭호·레벨
        g.setColor(TEXT); g.setFont(f(800, 68));
        text(g, fit(g, d.nickname(), W - 160), W / 2.0, 570, Align.CENTER);
        g.setFont(f(600, 30));
        String sub = en ? (d.title() != null ? d.title() + "  ·  " : "") + "Lv." + d.level()
                : (d.title() != null ? "「" + d.title() + "」  " : "") + "Lv." + d.level();
        double sw = g.getFontMetrics().stringWidth(sub) + 48;
        Shape pill = round((W - sw) / 2, 616, sw, 52, 26);
        g.setColor(new Color(63, 182, 139, 36)); g.fill(pill);
        g.setStroke(new BasicStroke(2)); g.setColor(new Color(63, 182, 139, 128)); g.draw(pill);
        g.setColor(Color.decode("#9fe0c3"));
        text(g, sub, W / 2.0, 643, Align.CENTER);

        // 큰 숫자: 유행도 | 순위(또는 7일 그래프)
        int boxY = 700, boxH = 240;
        Shape box = round(64, boxY, W - 128, boxH, 28);
        g.setColor(PANEL); g.fill(box);
        g.setColor(PANEL_LINE); g.draw(box);
        double half = (W - 128) / 2.0;
        g.setColor(MUTED); g.setFont(f(600, 28));
        text(g, en ? "Trend score" : "현재 유행도", 64 + half / 2, boxY + 48, Align.CENTER);
        text(g, d.rank() != null ? (en ? "Rank now" : "지금 순위") : (en ? "Last 7 days" : "최근 7일"), 64 + half * 1.5, boxY + 48, Align.CENTER);
        g.setColor(TEXT); g.setFont(f(800, 84));
        text(g, String.format(en ? java.util.Locale.ENGLISH : java.util.Locale.getDefault(), "%,d", d.score()), 64 + half / 2, boxY + 128, Align.CENTER);
        if (d.rank() != null) {
            g.setColor(d.rank() <= 3 ? Color.decode("#ffd27a") : TEXT);
            text(g, en ? "#" + d.rank() : d.rank() + "위", 64 + half * 1.5, boxY + 128, Align.CENTER);
            g.setColor(MUTED); g.setFont(f(500, 24));
            text(g, en ? "Leaderboard TOP 10" : "리더보드 TOP 10", 64 + half * 1.5, boxY + 190, Align.CENTER);
            sparkline(g, d.scores(), 64 + 60, boxY + 176, half - 120, 40);
        } else {
            sparkline(g, d.scores(), 64 + half + 40, boxY + 86, half - 80, 120);
        }
        g.setColor(PANEL_LINE); g.setStroke(new BasicStroke(2));
        g.draw(new Line2D.Double(64 + half, boxY + 30, 64 + half, boxY + boxH - 30));

        // 가진 🔥 키워드(그림 글자는 글꼴에 없어 작은 불꽃 점으로 그린다)
        double y = 985;
        if (!d.keywords().isEmpty()) {
            g.setColor(new Color(255, 140, 80)); g.fill(new Ellipse2D.Double(64, y - 8, 16, 16));
            g.setColor(MUTED); g.setFont(f(600, 26));
            text(g, en ? "Keywords I hold now" : "지금 가진 키워드", 92, y, Align.LEFT);
            y += 52;
            g.setFont(f(700, 30));
            double x = 64, maxEach = (W - 128 - 16.0 * (d.keywords().size() - 1)) / d.keywords().size();
            for (String k : d.keywords()) {
                String label = fit(g, "#" + k, maxEach - 44);
                double w = g.getFontMetrics().stringWidth(label) + 44;
                Shape chip = round(x, y - 30, w, 60, 30);
                g.setColor(new Color(255, 140, 80, 31)); g.fill(chip);
                g.setColor(new Color(255, 140, 80, 140)); g.draw(chip);
                g.setColor(Color.decode("#ffb48a"));
                text(g, label, x + 22, y + 1, Align.LEFT);
                x += w + 16;
            }
            y += 56;
        } else {
            y += 30;
        }

        // 기록 4칸(보유 키워드·추가수·처치·활동한 날)
        List<ShareDtos.Record> recs = d.records();
        int cols = Math.max(1, recs.size());
        double gap = 16, cw = (W - 128 - gap * (cols - 1)) / cols;
        for (int i = 0; i < recs.size(); i++) {
            double x = 64 + i * (cw + gap);
            g.setColor(PANEL); g.fill(round(x, y, cw, 104, 20));
            g.setColor(TEXT); g.setFont(f(800, 40));
            text(g, recs.get(i).value(), x + cw / 2, y + 42, Align.CENTER);
            g.setColor(MUTED); g.setFont(f(500, 22));
            text(g, recs.get(i).name(), x + cw / 2, y + 80, Align.CENTER);
        }

        // 바닥: 한 줄 홍보 + 주소
        g.setColor(ACCENT); g.setFont(f(800, 34));
        text(g, en ? "Know the trend. Grow bigger." : "트랜드를 아는 자, 커진다.", W / 2.0, H - 96, Align.CENTER);
        g.setColor(MUTED); g.setFont(f(500, 26));
        text(g, en ? "Challenge me at " + site + "!" : site + " 에서 나랑 겨뤄 봐!", W / 2.0, H - 50, Align.CENTER);
    }

    /** 원 + 이동 효과 + 거북이 + 테두리. 효과가 있으면 왼쪽 아래에서 따라온 모습이다 */
    private void circle(Graphics2D g, ShareDtos.CardData d, double cx, double cy, double r) {
        Color body = color(d.background(), Color.decode(TurtlePainter.BASE));
        double heading = 0.6;
        if (TrailPainter.known(d.trail())) {
            TrailPainter.paint(g, d.trail(), cx, cy, r, body);
            heading = TrailPainter.heading(cx, cy);
        }
        // 은은한 빛(canvas shadowBlur 대신 옅은 원 여러 겹)
        for (int i = 6; i >= 1; i--) {
            double rr = r + i * 10;
            g.setColor(new Color(63, 182, 139, 10));
            g.fill(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(body);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        if (d.artPixels() != null) {
            // 픽셀 그림(4.9): 원 안으로 잘라 칸이 또렷하게(보간 없이) 키워 그린다
            java.awt.Shape clip = g.getClip();
            g.clip(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(com.trendist.profile.support.PixelArt.image(d.artPalette(), d.artPixels()),
                    (int) Math.round(cx - r), (int) Math.round(cy - r), (int) Math.round(r * 2), (int) Math.round(r * 2), null);
            g.setClip(clip);
        } else {
            TurtlePainter.paint(g, cx, cy, r, d.level(), heading);
        }
        g.setColor(color(d.borderColor(), Color.decode("#11372a")));
        g.setStroke(new BasicStroke((float) TurtlePainter.borderWidth(r, d.borderWidth())));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    private static void sparkline(Graphics2D g, List<Long> values, double x, double y, double w, double h) {
        if (values == null || values.size() < 2) return;
        long min = values.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = values.stream().mapToLong(Long::longValue).max().orElse(0);
        double span = max - min == 0 ? 1 : max - min;
        int n = values.size();
        Path2D line = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double px = x + w * i / (n - 1), py = y + h - (values.get(i) - min) / span * h * 0.85 - h * 0.075;
            if (i == 0) line.moveTo(px, py); else line.lineTo(px, py);
        }
        Path2D area = new Path2D.Double(line);
        area.lineTo(x + w, y + h); area.lineTo(x, y + h); area.closePath();
        g.setPaint(new GradientPaint(0, (float) y, new Color(63, 182, 139, 89), 0, (float) (y + h), new Color(63, 182, 139, 0)));
        g.fill(area);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        double lx = x + w, ly = y + h - (values.get(n - 1) - min) / span * h * 0.85 - h * 0.075;
        g.fill(new Ellipse2D.Double(lx - 9, ly - 9, 18, 18));
    }
}
