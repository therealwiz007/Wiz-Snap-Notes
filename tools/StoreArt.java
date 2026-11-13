import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Play Store art for Wiz Snap Notes, drawn in code (Wiz black + yellow).
 *
 *   java tools/StoreArt.java icon    store-assets/icon-512.png 512
 *   java tools/StoreArt.java feature store-assets/feature-graphic-1024x500.png
 *   java tools/StoreArt.java frame   <in.png> <out.png> "<title>" "<subtitle>"
 */
public class StoreArt {
    static Color c(long argb) { return new Color((int) argb, true); }
    static final Color BG = c(0xFF0B0D10L), YELLOW = c(0xFFFFC400L), YELLOW_DEEP = c(0xFFE0A800L),
        MUTED = c(0xFF8B93A1L), TEXT = c(0xFFF4F5F7L), INK = c(0xFF0B0D10L);
    static final String SANS = pick("Helvetica Neue", "Helvetica", "Arial", "SansSerif");

    static String pick(String... names) {
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (have.contains(n)) return n;
        return "SansSerif";
    }

    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "icon" -> write(icon(Integer.parseInt(a[2])), a[1]);
            case "feature" -> write(feature(), a[1]);
            case "frame" -> write(frame(ImageIO.read(new File(a[1])), a[3], a[4]), a[2]);
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    static void write(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getAbsoluteFile().getParentFile().mkdirs();
        // Play wants opaque PNGs.
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(BG); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null); g.dispose();
        ImageIO.write(out, "png", f);
        System.out.println("wrote " + path + " " + img.getWidth() + "x" + img.getHeight());
    }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    // ---------- Icon: two stacked photo notes, the front one yellow with header band, lens, note lines ----------

    static void drawGlyph(Graphics2D g, double ox, double oy, double s) {
        AffineTransform old = g.getTransform();
        g.translate(ox, oy);
        g.scale(s, s);
        // Back card, tilted
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(9), .56, .52);
        g.setColor(c(0xFF2A2F38L));
        g.fill(new RoundRectangle2D.Double(.30, .17, .46, .62, .09, .09));
        g.setColor(c(0x33FFC400L)); g.setStroke(new BasicStroke(.008f));
        g.draw(new RoundRectangle2D.Double(.30, .17, .46, .62, .09, .09));
        g.setTransform(t);
        // Shadow + front card
        g.rotate(Math.toRadians(-5), .46, .52);
        g.setColor(c(0x66000000L));
        g.fill(new RoundRectangle2D.Double(.235, .225, .46, .62, .09, .09));
        RoundRectangle2D card = new RoundRectangle2D.Double(.22, .20, .46, .62, .09, .09);
        g.setPaint(new LinearGradientPaint(0f, .20f, 0f, .82f, new float[]{0f, 1f}, new Color[]{c(0xFFFFD43BL), YELLOW_DEEP}));
        g.fill(card);
        // Header band
        g.setColor(INK);
        g.fill(new RoundRectangle2D.Double(.26, .245, .38, .075, .03, .03));
        g.setColor(YELLOW);
        g.fill(new RoundRectangle2D.Double(.285, .268, .015, .03, .01, .01));
        g.setColor(c(0xFF8B93A1L));
        g.fill(new RoundRectangle2D.Double(.315, .272, .16, .022, .02, .02));
        // Lens
        double cx = .45, cy = .50;
        g.setColor(INK); g.fill(new Ellipse2D.Double(cx - .115, cy - .115, .23, .23));
        g.setColor(YELLOW); g.fill(new Ellipse2D.Double(cx - .068, cy - .068, .136, .136));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - .015, cy - .015), .05f, new float[]{0f, 1f},
            new Color[]{c(0xFF2A2F38L), INK}));
        g.fill(new Ellipse2D.Double(cx - .038, cy - .038, .076, .076));
        g.setColor(c(0x88FFFFFFL)); g.fill(new Ellipse2D.Double(cx - .075, cy - .085, .04, .028));
        // Note lines
        g.setColor(INK);
        g.fill(new RoundRectangle2D.Double(.26, .665, .38, .028, .028, .028));
        g.fill(new RoundRectangle2D.Double(.26, .725, .25, .028, .028, .028));
        g.setTransform(old);
    }

    static BufferedImage icon(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        g.setPaint(new LinearGradientPaint(0, 0, 0, s, new float[]{0f, 1f}, new Color[]{c(0xFF22262EL), BG}));
        g.fillRect(0, 0, s, s);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(s * .5, s * .55), s * .55f, new float[]{0f, 1f},
            new Color[]{c(0x38FFC400L), c(0x00FFC400L)}));
        g.fillRect(0, 0, s, s);
        drawGlyph(g, s * .03, s * -.01, s);
        g.dispose();
        return img;
    }

    static void drawIconTile(Graphics2D g, int x, int y, int size) {
        BufferedImage ic = icon(size);
        Shape clip = new RoundRectangle2D.Double(x, y, size, size, size * 0.42, size * 0.42);
        Shape old = g.getClip();
        g.setClip(clip); g.drawImage(ic, x, y, null); g.setClip(old);
        g.setColor(c(0x22FFFFFFL)); g.setStroke(new BasicStroke(2f)); g.draw(clip);
    }

    // ---------- Feature graphic ----------

    static void darkBackdrop(Graphics2D g, int w, int h, double gx, double gy, float radius) {
        g.setPaint(new LinearGradientPaint(0, 0, w, h, new float[]{0f, 1f}, new Color[]{c(0xFF191D24L), c(0xFF07080AL)}));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(gx, gy), radius, new float[]{0f, 1f},
            new Color[]{c(0x40FFC400L), c(0x00FFC400L)}));
        g.fillRect(0, 0, w, h);
    }

    static BufferedImage feature() {
        int W = 1024, H = 500;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        darkBackdrop(g, W, H, 200, 250, 330f);
        // Scan lines behind the tile: the "printing the stamp" sweep from the editor
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 5; i++) {
            g.setColor(c((0x30 - i * 8L) << 24 | 0xFFC400L));
            int y = 130 + i * 60;
            g.draw(new Line2D.Double(40, y, 360, y));
        }
        drawIconTile(g, 80, 130, 240);

        int tx = 410;
        g.setColor(TEXT);
        g.setFont(new Font(SANS, Font.BOLD, 62));
        g.drawString("Wiz Snap Notes", tx, 190);
        g.setColor(YELLOW);
        g.setFont(new Font(SANS, Font.BOLD, 29));
        g.drawString("Photo notes you can search and mail", tx, 242);
        g.setColor(MUTED);
        g.setFont(new Font(SANS, Font.PLAIN, 22));
        g.drawString("Header and notes stamped on every site photo", tx, 284);
        int x = tx;
        for (String chip : new String[]{"Stamped header", "Search", "Mail dealers"}) x = chip(g, x, 320, chip) + 14;
        g.dispose();
        return img;
    }

    static int chip(Graphics2D g, int x, int y, String text) {
        g.setFont(new Font(SANS, Font.BOLD, 21));
        int w = g.getFontMetrics().stringWidth(text) + 36;
        g.setColor(c(0xFF1F242CL)); g.fill(new RoundRectangle2D.Double(x, y, w, 44, 44, 44));
        g.setColor(c(0x66FFC400L)); g.setStroke(new BasicStroke(1.5f)); g.draw(new RoundRectangle2D.Double(x, y, w, 44, 44, 44));
        g.setColor(TEXT); g.drawString(text, x + 18, y + 29);
        return x + w;
    }

    // ---------- Framed screenshots (1080x1920, 9:16) ----------

    static BufferedImage frame(BufferedImage shot, String title, String subtitle) {
        int W = 1080, H = 1920;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        darkBackdrop(g, W, H, W / 2.0, 260, 700f);
        g.setColor(TEXT);
        g.setFont(new Font(SANS, Font.BOLD, 70));
        int y = 170;
        for (String line : wrap(g, title, W - 120)) { center(g, line, W, y); y += 82; }
        g.setColor(YELLOW);
        g.setFont(new Font(SANS, Font.PLAIN, 38));
        y += 4;
        for (String line : wrap(g, subtitle, W - 160)) { center(g, line, W, y); y += 50; }

        int top = Math.max(y + 30, 400);
        int sh = H - top + 60; // phone runs off the bottom edge
        int sw = (int) Math.round(shot.getWidth() * (sh / (double) shot.getHeight()));
        if (sw > 820) { sw = 820; sh = (int) Math.round(shot.getHeight() * (sw / (double) shot.getWidth())); }
        int sx = (W - sw) / 2, bezel = 18;
        RoundRectangle2D body = new RoundRectangle2D.Double(sx - bezel, top - bezel, sw + 2 * bezel, sh + 2 * bezel, 90, 90);
        g.setColor(c(0x66000000L)); g.fill(new RoundRectangle2D.Double(sx - bezel + 8, top - bezel + 16, sw + 2 * bezel, sh + 2 * bezel, 90, 90));
        g.setColor(c(0xFF1B1F26L)); g.fill(body);
        g.setColor(c(0xFF343A44L)); g.setStroke(new BasicStroke(3f)); g.draw(body);
        Shape screen = new RoundRectangle2D.Double(sx, top, sw, sh, 64, 64);
        Shape old = g.getClip();
        g.setClip(screen);
        g.drawImage(shot.getScaledInstance(sw, sh, Image.SCALE_SMOOTH), sx, top, null);
        g.setClip(old);
        g.dispose();
        return img;
    }

    static void center(Graphics2D g, String s, int w, int y) {
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    static List<String> wrap(Graphics2D g, String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            String t = line.isEmpty() ? w : line + " " + w;
            if (g.getFontMetrics().stringWidth(t) > max && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(t);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
