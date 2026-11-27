import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Draws the Google Play store art from the app's own drawings and font:
 *   icon-512.png             512 x 512, the launcher icon's yellow and guard (Play rounds the corners)
 *   feature-graphic.png      1024 x 500, shown at the top of the store page
 *
 * Run from the project root with the JDK that comes with Android Studio:
 *   & "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" docs\store\StoreArt.java
 */
public class StoreArt {
    static final Color LAUNCHER_YELLOW = new Color(0xFFFF00); // values/ic_launcher_background.xml
    static final Color BOARD = new Color(0x2A2D2F);           // dark theme page
    static final Color CHALK = new Color(0xEDEAE2);
    static final Color CHALK_SOFT = new Color(0xC9C5BB);
    static final Color CHALK_YELLOW = new Color(0xE8C55A);
    static final Color PAPER_CARD = new Color(0xFFFDF7);

    public static void main(String[] args) throws Exception {
        File out = new File("docs/store");
        BufferedImage guard = ImageIO.read(new File("art/originals/bell.png"));
        BufferedImage work = ImageIO.read(new File("art/originals/work.png"));
        BufferedImage study = ImageIO.read(new File("art/originals/study.png"));
        Font bold = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/gaegu_bold.ttf"));
        Font regular = Font.createFont(Font.TRUETYPE_FONT, new File("app/src/main/res/font/gaegu_regular.ttf"));

        ImageIO.write(icon(guard), "png", new File(out, "icon-512.png"));
        ImageIO.write(feature(guard, work, study, bold, regular), "png", new File(out, "feature-graphic.png"));
        System.out.println("Wrote docs/store/icon-512.png and docs/store/feature-graphic.png");
    }

    /** Full square, no rounding or shadow (Play applies its own mask), like the launcher icon. */
    static BufferedImage icon(BufferedImage guard) {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        g.setColor(LAUNCHER_YELLOW);
        g.fillRect(0, 0, 512, 512);
        Rectangle box = inkBounds(guard);
        drawFitted(g, guard, box, new Rectangle(76, 70, 360, 400));
        g.dispose();
        return opaque(img);
    }

    static BufferedImage feature(BufferedImage guard, BufferedImage work, BufferedImage study, Font bold, Font regular) {
        int w = 1024, h = 500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        g.setColor(BOARD);
        g.fillRect(0, 0, w, h);

        // The guard on the app's yellow disc, like Home.
        int cx = 250, cy = 250, r = 175;
        g.setColor(CHALK_YELLOW);
        g.fill(wobblyCircle(cx, cy, r, 7));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(CHALK);
        g.draw(wobblyCircle(cx, cy, r + 2, 11));
        drawFitted(g, guard, inkBounds(guard), new Rectangle(cx - 120, cy - 140, 240, 280));

        // Name and promise.
        g.setColor(CHALK);
        g.setFont(bold.deriveFont(96f));
        g.drawString("Snoozer", 470, 190);
        g.setFont(regular.deriveFont(40f));
        g.setColor(CHALK_SOFT);
        g.drawString("Quiet on schedule,", 474, 252);
        g.drawString("for work and study.", 474, 298);

        // The two categories' drawings on paper cards.
        drawCard(g, work, inkBounds(work), new Rectangle(474, 340, 120, 120), 3);
        drawCard(g, study, inkBounds(study), new Rectangle(618, 340, 120, 120), 5);
        g.dispose();
        return opaque(img);
    }

    static void drawCard(Graphics2D g, BufferedImage art, Rectangle ink, Rectangle at, int seed) {
        Shape card = new RoundRectangle2D.Float(at.x, at.y, at.width, at.height, 28, 28);
        g.setColor(PAPER_CARD);
        g.fill(card);
        g.setColor(CHALK);
        g.setStroke(new BasicStroke(3f));
        g.draw(card);
        drawFitted(g, art, ink, new Rectangle(at.x + 10, at.y + 10, at.width - 20, at.height - 20));
    }

    /** A circle with a hand-drawn wobble, so it matches the app's pencil shapes. */
    static Shape wobblyCircle(int cx, int cy, int r, long seed) {
        Random rnd = new Random(seed);
        Path2D.Float p = new Path2D.Float();
        int n = 10;
        double[] rr = new double[n];
        for (int i = 0; i < n; i++) rr[i] = r * (1 + (rnd.nextDouble() - 0.5) * 0.04);
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            double x = cx + rr[i % n] * Math.cos(a), y = cy + rr[i % n] * Math.sin(a);
            if (i == 0) p.moveTo(x, y);
            else {
                double am = 2 * Math.PI * (i - 0.5) / n;
                double rm = (rr[i % n] + rr[i - 1]) / 2 / Math.cos(Math.PI / n);
                p.quadTo(cx + rm * Math.cos(am), cy + rm * Math.sin(am), x, y);
            }
        }
        p.closePath();
        return p;
    }

    /** Scales the inked part of [src] to fit [dst], keeping its shape, centred. */
    static void drawFitted(Graphics2D g, BufferedImage src, Rectangle ink, Rectangle dst) {
        double s = Math.min(dst.width / (double) ink.width, dst.height / (double) ink.height);
        int w = (int) Math.round(ink.width * s), h = (int) Math.round(ink.height * s);
        int x = dst.x + (dst.width - w) / 2, y = dst.y + (dst.height - h) / 2;
        g.drawImage(src, x, y, x + w, y + h, ink.x, ink.y, ink.x + ink.width, ink.y + ink.height, null);
    }

    /** The bounding box of everything that isn't transparent or near-white. */
    static Rectangle inkBounds(BufferedImage img) {
        int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24, r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
                if (a > 40 && (r + gg + b) < 600) {
                    if (x < minX) minX = x; if (y < minY) minY = y;
                    if (x > maxX) maxX = x; if (y > maxY) maxY = y;
                }
            }
        int pad = 12;
        return new Rectangle(Math.max(0, minX - pad), Math.max(0, minY - pad),
            Math.min(img.getWidth(), maxX + pad) - Math.max(0, minX - pad),
            Math.min(img.getHeight(), maxY + pad) - Math.max(0, minY - pad));
    }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** Play wants the feature graphic without transparency; the icon may have it but needs none. */
    static BufferedImage opaque(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
