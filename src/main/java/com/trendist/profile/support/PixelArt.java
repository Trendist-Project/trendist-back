package com.trendist.profile.support;

import com.trendist.common.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * 픽셀 그림(4.9) 검사와 그리기. 32×32 칸, 색은 최대 32개. 칸마다 한 글자: 팔레트 번호(0-9, a-v) 또는 '.'(투명 = 원의 바탕색).
 * 서버가 이 값으로 PNG를 그리므로 클라이언트가 올린 이미지 파일을 그대로 퍼뜨리지 않는다.
 */
public final class PixelArt {
    private PixelArt() {}

    public static final int SIZE = 32;
    public static final int MAX_COLORS = 32;
    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuv";
    private static final Pattern HEX = Pattern.compile("^#[0-9a-f]{6}$");

    /** 검사를 통과한 그림: 쉼표로 이은 팔레트와 칸 글자. */
    public record Valid(String palette, String pixels) {}

    /** 형식 검사. 쓰지 않는 색은 팔레트에서 빼고 번호를 다시 매긴다. */
    public static Valid validate(List<String> palette, String pixels) {
        if (pixels == null || pixels.length() != SIZE * SIZE) throw ApiException.badRequest("ART_FORMAT", "그림 크기가 맞지 않아요");
        if (palette == null || palette.size() > MAX_COLORS) throw ApiException.badRequest("ART_COLORS", "색은 " + MAX_COLORS + "개까지 쓸 수 있어요");
        List<String> colors = new ArrayList<>();
        for (String c : palette) {
            String v = c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
            if (!HEX.matcher(v).matches()) throw ApiException.badRequest("ART_FORMAT", "색은 #RRGGBB 형식이어야 해요");
            colors.add(v);
        }
        // 쓴 색만 남기고(같은 색은 하나로) 번호를 다시 매긴다
        List<String> used = new ArrayList<>();
        StringBuilder out = new StringBuilder(SIZE * SIZE);
        boolean any = false;
        for (int i = 0; i < pixels.length(); i++) {
            char ch = pixels.charAt(i);
            if (ch == '.') { out.append('.'); continue; }
            int idx = DIGITS.indexOf(ch);
            if (idx < 0 || idx >= colors.size()) throw ApiException.badRequest("ART_FORMAT", "그림에 알 수 없는 칸이 있어요");
            String color = colors.get(idx);
            int n = used.indexOf(color);
            if (n < 0) { used.add(color); n = used.size() - 1; }
            out.append(DIGITS.charAt(n));
            any = true;
        }
        if (!any) throw ApiException.badRequest("ART_EMPTY", "빈 그림은 저장할 수 없어요. 한 칸 이상 칠해 주세요");
        return new Valid(String.join(",", used), out.toString());
    }

    public static List<String> paletteList(String palette) {
        return palette == null || palette.isEmpty() ? List.of() : List.of(palette.split(","));
    }

    /** 32×32 ARGB 이미지(투명 칸은 투명). */
    public static BufferedImage image(String palette, String pixels) {
        List<String> colors = paletteList(palette);
        int[] rgb = colors.stream().mapToInt(c -> 0xff000000 | Integer.parseInt(c.substring(1), 16)).toArray();
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < SIZE * SIZE; i++) {
            char ch = pixels.charAt(i);
            if (ch == '.') continue;
            int idx = DIGITS.indexOf(ch);
            if (idx >= 0 && idx < rgb.length) img.setRGB(i % SIZE, i / SIZE, rgb[idx]);
        }
        return img;
    }

    public static byte[] png(String palette, String pixels) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image(palette, pixels), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
