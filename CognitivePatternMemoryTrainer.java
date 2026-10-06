import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cognitive Pattern Memory Trainer  (v2)
 *
 * What's new:
 *  - Neon dark theme with animated floating background
 *  - Difficulty modes (Easy / Medium / Hard) with lives
 *  - Pop-in animated cards, countdown bar, "I'm Ready" skip, "Peek" hint
 *  - Click-to-fill quick-pick chips + Enter / Backspace navigation between slots
 *  - Per-slot review after every round (green = right, red = wrong, shows the answer)
 *  - Speed + streak + difficulty score bonuses, animated score counter
 *  - Final score card: rank, stars, stats, personalised message, confetti on records
 */
public class CognitivePatternMemoryTrainer {

    // =====================================================================
    //  THEME
    // =====================================================================
    static final class Theme {
        static final Color BG_TOP    = new Color(12, 10, 38);
        static final Color BG_BOTTOM = new Color(58, 28, 112);
        static final Color CYAN   = new Color(0, 220, 255);
        static final Color PINK   = new Color(255, 79, 154);
        static final Color AMBER  = new Color(255, 196, 61);
        static final Color GREEN  = new Color(46, 213, 115);
        static final Color RED    = new Color(255, 82, 82);
        static final Color VIOLET = new Color(139, 92, 246);
        static final Color TEXT   = new Color(245, 243, 255);
        static final Color MUTED  = new Color(180, 172, 220);
        static final Color DIM    = new Color(88, 78, 150);

        private static Font base;

        static Font font(int style, float size) {
            if (base == null) {
                try {
                    File f = new File("Coddex.ttf");
                    if (f.exists()) base = Font.createFont(Font.TRUETYPE_FONT, f);
                } catch (Exception ignored) { }
                if (base == null) base = new Font("SansSerif", Font.PLAIN, 14);
            }
            return base.deriveFont(style, size);
        }

        static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

        static Color lighter(Color c, int amt) {
            return new Color(Math.min(255, c.getRed() + amt), Math.min(255, c.getGreen() + amt), Math.min(255, c.getBlue() + amt));
        }

        static boolean isLight(Color c) { return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) > 150; }

        static void aa(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        static void drawCentered(Graphics2D g, String s, float cx, float baseline) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s) / 2f, baseline);
        }
    }

    static JPanel clear(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    static JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    // =====================================================================
    //  MODELS
    // =====================================================================
    static final String[] SYMBOLS = {"@", "#", "$", "%", "&", "*", "!"};
    static final Map<String, Color> COLOR_MAP = new LinkedHashMap<>();
    static {
        COLOR_MAP.put("Red", new Color(239, 68, 68));
        COLOR_MAP.put("Blue", new Color(59, 130, 246));
        COLOR_MAP.put("Green", new Color(34, 197, 94));
        COLOR_MAP.put("Yellow", new Color(250, 204, 21));
        COLOR_MAP.put("Purple", new Color(168, 85, 247));
        COLOR_MAP.put("Orange", new Color(249, 115, 22));
    }

    static abstract class PatternElement implements Serializable {
        abstract String display();
        abstract String value();
        abstract Color accent();
        abstract String kind();
    }

    static class NumberElement extends PatternElement {
        private final int number;
        NumberElement(int number) { this.number = number; }
        @Override String display() { return Integer.toString(number); }
        @Override String value() { return Integer.toString(number); }
        @Override Color accent() { return Theme.CYAN; }
        @Override String kind() { return "NUMBER"; }
    }

    static class ColorElement extends PatternElement {
        private final String colorName;
        ColorElement(String colorName) { this.colorName = colorName; }
        @Override String display() { return colorName; }
        @Override String value() { return colorName; }
        @Override Color accent() { return COLOR_MAP.get(colorName); }
        @Override String kind() { return "COLOR"; }
    }

    static class SymbolElement extends PatternElement {
        private final String symbol;
        SymbolElement(String symbol) { this.symbol = symbol; }
        @Override String display() { return symbol; }
        @Override String value() { return symbol; }
        @Override Color accent() { return Theme.AMBER; }
        @Override String kind() { return "SYMBOL"; }
    }

    static class SequencePattern implements Serializable {
        private final List<PatternElement> sequence;
        SequencePattern(List<PatternElement> seq) { this.sequence = seq; }
        List<PatternElement> getSequence() { return sequence; }

        boolean[] check(List<String> inputs) {
            boolean[] ok = new boolean[sequence.size()];
            for (int i = 0; i < ok.length; i++) {
                String expected = sequence.get(i).value().trim().toLowerCase();
                String got = i < inputs.size() ? inputs.get(i).trim().toLowerCase() : "";
                ok[i] = expected.equals(got);
            }
            return ok;
        }

        boolean validate(List<String> inputs) {
            if (inputs.size() != sequence.size()) return false;
            for (boolean b : check(inputs)) if (!b) return false;
            return true;
        }
    }

    enum Difficulty {
        EASY("Easy", 3, 900, 5, 1.0, Theme.GREEN, "3 items to start  |  5 lives  |  relaxed reveal  |  x1 score"),
        MEDIUM("Medium", 4, 700, 3, 1.5, Theme.AMBER, "4 items to start  |  3 lives  |  faster reveal  |  x1.5 score"),
        HARD("Hard", 5, 500, 3, 2.0, Theme.RED, "5 items to start  |  3 lives  |  blink-and-miss  |  x2 score");

        final String label; final int startLen; final int msPerItem; final int lives;
        final double mult; final Color color; final String blurb;

        Difficulty(String label, int startLen, int msPerItem, int lives, double mult, Color color, String blurb) {
            this.label = label; this.startLen = startLen; this.msPerItem = msPerItem;
            this.lives = lives; this.mult = mult; this.color = color; this.blurb = blurb;
        }

        static Difficulty fromLabel(String s) {
            for (Difficulty d : values()) if (d.label.equalsIgnoreCase(s)) return d;
            return EASY;
        }
    }

    static class Player implements Serializable {
        private static final long serialVersionUID = 2L;
        int highScore = 0;
        int bestStreak = 0;
        int gamesPlayed = 0;
        String difficulty = "Easy";
    }

    static class ResultData {
        int score, highScore, rounds, correctRounds, bestStreak, longestSeq;
        boolean newRecord;
        Difficulty difficulty;
    }

    static class Persistence {
        private static final String SAVE_FILE = System.getProperty("user.home") + File.separator + ".cognitive_trainer_save_v2";

        static void savePlayer(Player p) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
                oos.writeObject(p);
            } catch (Exception ignored) { }
        }

        static Player loadPlayer() {
            File f = new File(SAVE_FILE);
            if (!f.exists()) return new Player();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Object o = ois.readObject();
                if (o instanceof Player) return (Player) o;
            } catch (Exception ignored) { }
            return new Player();
        }
    }

    // =====================================================================
    //  REUSABLE UI PARTS
    // =====================================================================

    /** Animated gradient background with floating bubbles + confetti bursts. */
    static class AnimatedBackground extends JPanel {
        private static final class Dot { float x, y, r, vy, phase; int a; Color c; }
        private static final class Conf { float x, y, vx, vy, rot, vr, size; Color c; }

        private final List<Dot> dots = new ArrayList<>();
        private final List<Conf> confetti = new ArrayList<>();
        private final Random rnd = new Random();
        private final javax.swing.Timer timer;
        private long tick = 0;

        AnimatedBackground() {
            setOpaque(true);
            Color[] pal = {Theme.CYAN, Theme.PINK, Theme.VIOLET, Theme.AMBER};
            for (int i = 0; i < 34; i++) {
                Dot d = new Dot();
                d.x = rnd.nextFloat(); d.y = rnd.nextFloat();
                d.r = 6 + rnd.nextFloat() * 26;
                d.vy = 0.0004f + rnd.nextFloat() * 0.0013f;
                d.phase = rnd.nextFloat() * 6.28f;
                d.a = 22 + rnd.nextInt(40);
                d.c = pal[rnd.nextInt(pal.length)];
                dots.add(d);
            }
            timer = new javax.swing.Timer(33, e -> step());
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (isShowing()) timer.start(); else timer.stop();
                }
            });
        }

        void burst(int count) {
            Color[] pal = {Theme.CYAN, Theme.PINK, Theme.AMBER, Theme.GREEN, Theme.VIOLET, Color.WHITE};
            int w = Math.max(getWidth(), 600);
            for (int i = 0; i < count; i++) {
                Conf c = new Conf();
                c.x = rnd.nextFloat() * w; c.y = -10 - rnd.nextFloat() * 120;
                c.vx = (rnd.nextFloat() - 0.5f) * 5f; c.vy = 2f + rnd.nextFloat() * 4f;
                c.rot = rnd.nextFloat() * 6.28f; c.vr = (rnd.nextFloat() - 0.5f) * 0.4f;
                c.size = 6 + rnd.nextFloat() * 8;
                c.c = pal[rnd.nextInt(pal.length)];
                confetti.add(c);
            }
        }

        private void step() {
            tick++;
            for (Dot d : dots) {
                d.y -= d.vy;
                if (d.y < -0.1f) { d.y = 1.1f; d.x = rnd.nextFloat(); }
            }
            for (int i = confetti.size() - 1; i >= 0; i--) {
                Conf c = confetti.get(i);
                c.x += c.vx; c.y += c.vy; c.vy += 0.06f; c.rot += c.vr;
                if (c.y > getHeight() + 30) confetti.remove(i);
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            g2.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, getHeight(), Theme.BG_BOTTOM));
            g2.fillRect(0, 0, getWidth(), getHeight());
            for (Dot d : dots) {
                float px = d.x * getWidth() + (float) Math.sin(tick * 0.02 + d.phase) * 14f;
                float py = d.y * getHeight();
                g2.setColor(Theme.alpha(d.c, d.a));
                g2.fill(new Ellipse2D.Float(px - d.r, py - d.r, d.r * 2, d.r * 2));
            }
            for (Conf c : confetti) {
                AffineTransform old = g2.getTransform();
                g2.translate(c.x, c.y);
                g2.rotate(c.rot);
                g2.setColor(c.c);
                g2.fill(new Rectangle2D.Float(-c.size / 2, -c.size / 4, c.size, c.size / 2));
                g2.setTransform(old);
            }
            g2.dispose();
        }
    }

    /** Frosted-glass rounded panel. */
    static class GlassPanel extends JPanel {
        GlassPanel(LayoutManager lm) { super(lm); setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            RoundRectangle2D rr = new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 28, 28);
            g2.setColor(new Color(255, 255, 255, 26));
            g2.fill(rr);
            g2.setColor(new Color(255, 255, 255, 70));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(rr);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Pill-shaped button with hover grow / press shrink animation. */
    static class RoundButton extends JButton {
        private float scale = 1f, target = 1f;
        private Color base;
        private final boolean compact;
        private final javax.swing.Timer anim;
        private boolean hover;

        RoundButton(String text, Color base, boolean compact) {
            super(text);
            this.base = base; this.compact = compact;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setFocusable(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.font(Font.BOLD, compact ? 15f : 19f));
            anim = new javax.swing.Timer(15, e -> {
                scale += (target - scale) * 0.3f;
                if (Math.abs(target - scale) < 0.004f) { scale = target; ((javax.swing.Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; target = 1.06f; anim.start(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; target = 1f; anim.start(); }
                @Override public void mousePressed(MouseEvent e) { target = 0.95f; anim.start(); }
                @Override public void mouseReleased(MouseEvent e) { target = hover ? 1.06f : 1f; anim.start(); }
            });
        }

        void setBase(Color c) { base = c; repaint(); }

        @Override public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Math.max(compact ? 64 : 160, fm.stringWidth(getText()) + (compact ? 30 : 60)), compact ? 38 : 52);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            int w = getWidth(), h = getHeight();
            g2.translate(w / 2.0, h / 2.0); g2.scale(scale, scale); g2.translate(-w / 2.0, -h / 2.0);
            Color c = isEnabled() ? base : new Color(90, 85, 125);
            float bh = h - 9;
            RoundRectangle2D rr = new RoundRectangle2D.Float(4, 3, w - 8, bh, bh, bh);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fill(new RoundRectangle2D.Float(4, 7, w - 8, bh, bh, bh));
            g2.setPaint(new GradientPaint(0, 3, Theme.lighter(c, 35), 0, 3 + bh, c));
            g2.fill(rr);
            g2.setColor(new Color(255, 255, 255, 60));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(rr);
            g2.setFont(getFont());
            g2.setColor(!isEnabled() ? new Color(170, 165, 200) : (Theme.isLight(c) ? new Color(30, 20, 50) : Color.WHITE));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2f, 3 + (bh + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    /** Gradient-filled title text. */
    static class GradientLabel extends JComponent {
        private final String text;
        GradientLabel(String text, float size) {
            this.text = text;
            setFont(Theme.font(Font.BOLD, size));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 24, fm.getHeight() + 8);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawString(text, x + 2, y + 3);
            g2.setPaint(new GradientPaint(x, 0, Theme.CYAN, x + fm.stringWidth(text), 0, Theme.PINK));
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    /** Caption + big value, used in HUD and result tiles. */
    static class StatBox extends JPanel {
        private final JLabel val;
        StatBox(String caption, Color valueColor) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JLabel cap = label(caption, Font.BOLD, 11f, Theme.MUTED);
            val = label("0", Font.BOLD, 24f, valueColor);
            add(cap);
            add(val);
        }
        void set(String s) { val.setText(s); }
    }

    /** Thin countdown bar. */
    static class ThinBar extends JComponent {
        private float value = 0f;
        ThinBar() {
            setPreferredSize(new Dimension(520, 12));
            setMaximumSize(new Dimension(520, 12));
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        void setValue(float v) { value = Math.max(0f, Math.min(1f, v)); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(0, 0, w, h, h, h);
            int fw = (int) (w * value);
            if (fw > 0) {
                g2.setPaint(value < 0.25f ? new GradientPaint(0, 0, Theme.RED, w, 0, Theme.AMBER)
                                           : new GradientPaint(0, 0, Theme.CYAN, w, 0, Theme.PINK));
                g2.fillRoundRect(0, 0, fw, h, h, h);
            }
            g2.dispose();
        }
    }

    /** A pattern element drawn as a pop-in card. */
    static class ElementCard extends JComponent {
        private final PatternElement el;
        private float pop = 1f;
        private int state = 0; // 0 neutral, 1 correct, 2 wrong
        private String subtitle = "";

        ElementCard(PatternElement el) {
            this.el = el;
            setPreferredSize(new Dimension(112, 138));
        }

        void setState(int s) { state = s; repaint(); }
        void setSubtitle(String s) { subtitle = s; repaint(); }

        void popIn(int delayMs) {
            pop = 0f;
            javax.swing.Timer d = new javax.swing.Timer(Math.max(1, delayMs), e -> {
                javax.swing.Timer a = new javax.swing.Timer(16, null);
                a.addActionListener(ev -> {
                    pop += 0.09f;
                    if (pop >= 1f) { pop = 1f; a.stop(); }
                    repaint();
                });
                a.start();
            });
            d.setRepeats(false);
            d.start();
        }

        private static float easeOutBack(float t) {
            float c1 = 1.70158f, c3 = c1 + 1f;
            double p = t - 1;
            return (float) (1 + c3 * p * p * p + c1 * p * p);
        }

        @Override protected void paintComponent(Graphics g) {
            if (pop <= 0f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            float s = easeOutBack(Math.min(1f, pop));
            double cx = getWidth() / 2.0, cy = 57;
            g2.translate(cx, cy); g2.scale(s, s); g2.translate(-cx, -cy);

            boolean isColor = el instanceof ColorElement;
            Color accent = el.accent();
            RoundRectangle2D rr = new RoundRectangle2D.Float(4, 4, 104, 106, 26, 26);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fill(new RoundRectangle2D.Float(4, 8, 104, 106, 26, 26));
            if (isColor) g2.setPaint(new GradientPaint(0, 4, Theme.lighter(accent, 40), 0, 110, accent));
            else g2.setPaint(new GradientPaint(0, 4, new Color(78, 62, 160), 0, 110, new Color(36, 28, 90)));
            g2.fill(rr);

            Color border = state == 1 ? Theme.GREEN : state == 2 ? Theme.RED : Theme.alpha(Theme.lighter(accent, 20), 210);
            g2.setStroke(new BasicStroke(state == 0 ? 2f : 4f));
            g2.setColor(border);
            g2.draw(rr);

            Color txt = isColor && Theme.isLight(accent) ? new Color(30, 20, 50) : Color.WHITE;
            g2.setFont(Theme.font(Font.BOLD, 10f));
            g2.setColor(isColor ? Theme.alpha(txt, 190) : Theme.MUTED);
            Theme.drawCentered(g2, el.kind(), 56, 23);

            float size = el instanceof NumberElement ? 38f : el instanceof SymbolElement ? 54f : 21f;
            g2.setFont(Theme.font(Font.BOLD, size));
            g2.setColor(isColor ? txt : accent);
            FontMetrics fm = g2.getFontMetrics();
            Theme.drawCentered(g2, el.display(), 56, 61 + (fm.getAscent() - fm.getDescent()) / 2f);

            if (!subtitle.isEmpty()) {
                g2.setFont(Theme.font(Font.BOLD, 12f));
                g2.setColor(state == 1 ? Theme.GREEN : Theme.RED);
                Theme.drawCentered(g2, subtitle, 56, 130);
            }
            g2.dispose();
        }
    }

    /** Text slot for answers. */
    static class SlotField extends JTextField {
        int state = 0;
        SlotField() {
            super(5);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setFont(Theme.font(Font.BOLD, 24f));
            setHorizontalAlignment(SwingConstants.CENTER);
            setForeground(Theme.TEXT);
            setCaretColor(Theme.CYAN);
            setPreferredSize(new Dimension(120, 64));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            g2.setColor(new Color(255, 255, 255, hasFocus() ? 48 : 28));
            g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
            g2.dispose();
            super.paintComponent(g);
            g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            g2.setStroke(new BasicStroke(hasFocus() ? 3f : 1.6f));
            g2.setColor(hasFocus() ? Theme.CYAN : new Color(255, 255, 255, 90));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
            g2.dispose();
        }
    }

    /** 5-star rating that lights up one at a time. */
    static class StarsPanel extends JComponent {
        private int lit = 0;
        StarsPanel() { setPreferredSize(new Dimension(5 * 56, 56)); setAlignmentX(Component.CENTER_ALIGNMENT); }
        void setLit(int n) { lit = n; repaint(); }

        private static Path2D star(double cx, double cy, double R) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5;
                double r = (i % 2 == 0) ? R : R * 0.45;
                double x = cx + Math.cos(ang) * r, y = cy + Math.sin(ang) * r;
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.closePath();
            return p;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            int total = 5 * 56;
            int x0 = (getWidth() - total) / 2;
            for (int i = 0; i < 5; i++) {
                Path2D s = star(x0 + i * 56 + 28, 28, 24);
                if (i < lit) {
                    g2.setPaint(new GradientPaint(0, 4, new Color(255, 230, 120), 0, 52, Theme.AMBER));
                    g2.fill(s);
                    g2.setColor(new Color(255, 255, 255, 140));
                } else {
                    g2.setColor(new Color(255, 255, 255, 35));
                    g2.fill(s);
                    g2.setColor(new Color(255, 255, 255, 80));
                }
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(s);
            }
            g2.dispose();
        }
    }

    // =====================================================================
    //  FRAME
    // =====================================================================
    static class GameFrame extends JFrame {
        private final CardLayout cardLayout = new CardLayout();
        private final JPanel cardPanel = new JPanel(cardLayout);
        private final Player player;
        private final MainMenu menu;
        private final GamePage game;
        private final ResultPage result;

        GameFrame(Player savedPlayer) {
            super("Cognitive Pattern Memory Trainer");
            this.player = savedPlayer;
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1080, 760);
            setMinimumSize(new Dimension(920, 680));
            setLocationRelativeTo(null);
            menu = new MainMenu(this);
            game = new GamePage(this);
            result = new ResultPage(this);
            cardPanel.add(menu, "menu");
            cardPanel.add(game, "game");
            cardPanel.add(result, "result");
            add(cardPanel);
            showMenu();
        }

        Player getPlayer() { return player; }
        void showMenu() { menu.refresh(); cardLayout.show(cardPanel, "menu"); }
        void startGame() { cardLayout.show(cardPanel, "game"); game.startGame(Difficulty.fromLabel(player.difficulty)); }
        void showResult(ResultData d) { result.present(d); cardLayout.show(cardPanel, "result"); }
    }

    // =====================================================================
    //  MAIN MENU
    // =====================================================================
    static class MainMenu extends AnimatedBackground {
        private final GameFrame parent;
        private final JLabel statsLabel = label(" ", Font.BOLD, 15f, Theme.AMBER);
        private final JLabel blurb = label(" ", Font.PLAIN, 14f, Theme.MUTED);
        private final Map<Difficulty, RoundButton> diffButtons = new LinkedHashMap<>();

        MainMenu(GameFrame parent) {
            this.parent = parent;
            setLayout(new GridBagLayout());

            JPanel box = vbox();
            box.add(new GradientLabel("Cognitive Pattern", 54f));
            box.add(new GradientLabel("MEMORY TRAINER", 30f));
            box.add(Box.createVerticalStrut(8));
            box.add(label("Memorize. Recall. Level up your mind.", Font.PLAIN, 18f, Theme.TEXT));
            box.add(Box.createVerticalStrut(26));

            GlassPanel pick = new GlassPanel(new BorderLayout());
            pick.setBorder(BorderFactory.createEmptyBorder(16, 28, 16, 28));
            JPanel inner = vbox();
            inner.add(label("CHOOSE DIFFICULTY", Font.BOLD, 12f, Theme.MUTED));
            inner.add(Box.createVerticalStrut(10));
            JPanel row = clear(new FlowLayout(FlowLayout.CENTER, 12, 0));
            for (Difficulty d : Difficulty.values()) {
                RoundButton b = new RoundButton(d.label, d.color, false);
                b.setPreferredSize(new Dimension(130, 48));
                b.addActionListener(e -> { parent.getPlayer().difficulty = d.label; updateDiff(); });
                diffButtons.put(d, b);
                row.add(b);
            }
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(row);
            inner.add(Box.createVerticalStrut(10));
            inner.add(blurb);
            pick.add(inner, BorderLayout.CENTER);
            pick.setAlignmentX(Component.CENTER_ALIGNMENT);
            box.add(pick);

            box.add(Box.createVerticalStrut(18));
            box.add(statsLabel);
            box.add(Box.createVerticalStrut(18));

            RoundButton start = new RoundButton("Start Game", Theme.PINK, false);
            start.setPreferredSize(new Dimension(240, 60));
            start.setFont(Theme.font(Font.BOLD, 24f));
            start.setAlignmentX(Component.CENTER_ALIGNMENT);
            start.addActionListener(e -> {
                Persistence.savePlayer(parent.getPlayer());
                parent.startGame();
            });
            box.add(start);

            box.add(Box.createVerticalStrut(22));
            box.add(label("Watch the cards  >  type them back in order  >  longer sequences each level", Font.PLAIN, 14f, Theme.MUTED));
            box.add(label("Tip: press Enter to jump between slots. Click the quick-pick chips to fill instantly.", Font.PLAIN, 13f, Theme.DIM));
            add(box);
            updateDiff();
        }

        private void updateDiff() {
            Difficulty cur = Difficulty.fromLabel(parent.getPlayer().difficulty);
            for (Map.Entry<Difficulty, RoundButton> en : diffButtons.entrySet())
                en.getValue().setBase(en.getKey() == cur ? en.getKey().color : Theme.DIM);
            blurb.setText(cur.blurb);
        }

        void refresh() {
            Player p = parent.getPlayer();
            statsLabel.setText("High Score  " + p.highScore + "     |     Best Streak  " + p.bestStreak + "     |     Games  " + p.gamesPlayed);
            updateDiff();
        }
    }

    // =====================================================================
    //  GAME PAGE
    // =====================================================================
    static class GamePage extends AnimatedBackground {
        private enum Phase { IDLE, COUNTDOWN, MEMORIZE, ANSWER, PEEK, FEEDBACK, OVER }
        private static final int MAX_LEN = 10;
        private static final String[] PRAISE = {"Perfect!", "Sharp mind!", "Nailed it!", "Brilliant recall!", "On fire!", "Razor focus!"};

        private final GameFrame parent;
        private final Player player;
        private final Random rnd = new Random();

        // HUD
        private final StatBox scoreBox = new StatBox("SCORE", Theme.TEXT);
        private final StatBox bestBox = new StatBox("HIGH SCORE", Theme.AMBER);
        private final StatBox levelBox = new StatBox("LEVEL", Theme.CYAN);
        private final StatBox streakBox = new StatBox("STREAK", Theme.GREEN);
        private final StatBox livesBox = new StatBox("LIVES", Theme.PINK);

        private final JLabel promptLabel = label(" ", Font.BOLD, 30f, Theme.TEXT);
        private final JLabel subLabel = label(" ", Font.PLAIN, 15f, Theme.MUTED);
        private final ThinBar bar = new ThinBar();
        private final JPanel stage = new JPanel(new GridBagLayout());
        private final JPanel palette;
        private final RoundButton primaryBtn = new RoundButton("...", Theme.CYAN, false);
        private final RoundButton peekBtn = new RoundButton("Peek (-10)", Theme.AMBER, true);
        private final RoundButton menuBtn = new RoundButton("Quit to Menu", Theme.DIM, true);

        // state
        private Phase phase = Phase.IDLE;
        private int roundId = 0;
        private Difficulty diff = Difficulty.EASY;
        private int score, displayScore, lives, streak, bestStreakSession, seqLen, rounds, correctRounds, longestSeq, recordAtStart;
        private boolean peeked, committed;
        private long answerStart;
        private List<PatternElement> seq = new ArrayList<>();
        private final List<SlotField> fields = new ArrayList<>();
        private int focusIdx = 0;
        private javax.swing.Timer scoreTimer;

        GamePage(GameFrame parent) {
            this.parent = parent;
            this.player = parent.getPlayer();
            setLayout(new BorderLayout(0, 8));
            setBorder(BorderFactory.createEmptyBorder(16, 28, 16, 28));

            // HUD
            GlassPanel hud = new GlassPanel(new GridLayout(1, 5, 10, 0));
            hud.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            hud.add(scoreBox); hud.add(bestBox); hud.add(levelBox); hud.add(streakBox); hud.add(livesBox);
            add(hud, BorderLayout.NORTH);

            // centre column
            JPanel center = clear(new BorderLayout(0, 6));
            JPanel head = vbox();
            head.add(Box.createVerticalStrut(8));
            head.add(promptLabel);
            head.add(Box.createVerticalStrut(4));
            head.add(subLabel);
            head.add(Box.createVerticalStrut(10));
            head.add(bar);
            center.add(head, BorderLayout.NORTH);
            stage.setOpaque(false);
            center.add(stage, BorderLayout.CENTER);

            palette = buildPalette();
            palette.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(palette, BorderLayout.SOUTH);
            add(center, BorderLayout.CENTER);

            // bottom buttons
            JPanel bottom = clear(new FlowLayout(FlowLayout.CENTER, 16, 4));
            bottom.add(menuBtn);
            bottom.add(peekBtn);
            primaryBtn.setPreferredSize(new Dimension(220, 52));
            bottom.add(primaryBtn);
            add(bottom, BorderLayout.SOUTH);

            primaryBtn.addActionListener(e -> primaryAction());
            peekBtn.addActionListener(e -> peek());
            menuBtn.addActionListener(e -> {
                roundId++;
                phase = Phase.IDLE;
                commit();
                parent.showMenu();
            });

            // Enter = next step whenever the text slots aren't handling it
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "primary");
            getActionMap().put("primary", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (phase != Phase.ANSWER && phase != Phase.IDLE && primaryBtn.isEnabled()) primaryAction();
                }
            });
        }

        private JPanel buildPalette() {
            JPanel p = vbox();
            JPanel colors = clear(new FlowLayout(FlowLayout.CENTER, 8, 4));
            for (String name : COLOR_MAP.keySet()) {
                RoundButton b = new RoundButton(name, COLOR_MAP.get(name), true);
                b.addActionListener(e -> fillFocused(name));
                colors.add(b);
            }
            JPanel syms = clear(new FlowLayout(FlowLayout.CENTER, 8, 4));
            for (String s : SYMBOLS) {
                RoundButton b = new RoundButton(s, new Color(98, 84, 190), true);
                b.addActionListener(e -> fillFocused(s));
                syms.add(b);
            }
            p.add(colors);
            p.add(syms);
            p.setVisible(false);
            return p;
        }

        // ---------------------------------------------------------- helpers
        private void setStage(JComponent c) {
            stage.removeAll();
            stage.add(c);
            stage.revalidate();
            stage.repaint();
        }

        private JComponent rowOf(List<? extends JComponent> items) {
            JPanel col = vbox();
            int perRow = items.size() > 6 ? (int) Math.ceil(items.size() / 2.0) : Math.max(1, items.size());
            for (int i = 0; i < items.size(); i += perRow) {
                JPanel r = clear(new FlowLayout(FlowLayout.CENTER, 12, 8));
                for (int j = i; j < Math.min(items.size(), i + perRow); j++) r.add(items.get(j));
                r.setAlignmentX(Component.CENTER_ALIGNMENT);
                col.add(r);
            }
            return col;
        }

        private void setPrimary(String text, Color c, boolean enabled) {
            primaryBtn.setText(text);
            primaryBtn.setBase(c);
            primaryBtn.setEnabled(enabled);
        }

        private void shake(JComponent c) {
            final int[] n = {0};
            javax.swing.Timer t = new javax.swing.Timer(35, null);
            t.addActionListener(e -> {
                n[0]++;
                int off = (n[0] % 2 == 0 ? 1 : -1) * Math.max(0, 16 - n[0] * 2);
                c.setBorder(BorderFactory.createEmptyBorder(0, Math.max(0, off), 0, Math.max(0, -off)));
                c.revalidate();
                if (n[0] >= 9) { c.setBorder(null); c.revalidate(); t.stop(); }
            });
            t.start();
        }

        private void refreshHud() {
            scoreBox.set(String.valueOf(displayScore));
            bestBox.set(String.valueOf(Math.max(player.highScore, score)));
            levelBox.set(String.valueOf(seqLen - diff.startLen + 1));
            streakBox.set("x" + streak);
            StringBuilder h = new StringBuilder();
            for (int i = 0; i < diff.lives; i++) h.append(i < lives ? "\u2665 " : "\u2661 ");
            livesBox.set(h.toString().trim());
        }

        private void animateScore() {
            if (scoreTimer != null) scoreTimer.stop();
            scoreTimer = new javax.swing.Timer(20, null);
            scoreTimer.addActionListener(e -> {
                int d = score - displayScore;
                if (d == 0) { scoreTimer.stop(); return; }
                displayScore += (d > 0 ? 1 : -1) * Math.max(1, Math.abs(d) / 8);
                refreshHud();
            });
            scoreTimer.start();
        }

        // ---------------------------------------------------------- flow
        void startGame(Difficulty d) {
            diff = d;
            roundId++;
            score = displayScore = 0;
            lives = d.lives;
            streak = bestStreakSession = 0;
            seqLen = d.startLen;
            rounds = correctRounds = longestSeq = 0;
            recordAtStart = player.highScore;
            committed = false;
            refreshHud();
            countdown();
        }

        private void countdown() {
            phase = Phase.COUNTDOWN;
            final int id = ++roundId;
            palette.setVisible(false);
            peekBtn.setVisible(false);
            bar.setVisible(true);
            bar.setValue(0);
            promptLabel.setText("Get ready!");
            promptLabel.setForeground(Theme.TEXT);
            subLabel.setText(diff.label + " mode  -  starting with " + seqLen + " items");
            setPrimary("Get ready...", Theme.DIM, false);
            JLabel big = label("3", Font.BOLD, 120f, Theme.CYAN);
            setStage(big);

            final String[] steps = {"3", "2", "1", "GO!"};
            final int[] i = {0};
            javax.swing.Timer t = new javax.swing.Timer(650, null);
            t.addActionListener(e -> {
                if (id != roundId) { t.stop(); return; }
                i[0]++;
                if (i[0] >= steps.length) { t.stop(); startRound(); return; }
                big.setText(steps[i[0]]);
                if (i[0] == 3) big.setForeground(Theme.GREEN);
            });
            t.start();
        }

        private List<PatternElement> generateSequence(int length) {
            List<PatternElement> out = new ArrayList<>();
            String[] names = COLOR_MAP.keySet().toArray(new String[0]);
            for (int i = 0; i < length; i++) {
                int type = rnd.nextInt(3);
                if (type == 0) out.add(new NumberElement(rnd.nextInt(90) + 10));
                else if (type == 1) out.add(new ColorElement(names[rnd.nextInt(names.length)]));
                else out.add(new SymbolElement(SYMBOLS[rnd.nextInt(SYMBOLS.length)]));
            }
            return out;
        }

        private void startRound() {
            phase = Phase.MEMORIZE;
            final int id = ++roundId;
            peeked = false;
            seq = generateSequence(seqLen);
            palette.setVisible(false);
            peekBtn.setVisible(false);
            bar.setVisible(true);
            promptLabel.setText("Memorize the sequence!");
            promptLabel.setForeground(Theme.TEXT);
            subLabel.setText("Round " + (rounds + 1) + "  |  " + seqLen + " items  |  press Ready when you've got it");
            setPrimary("I'm Ready  >", Theme.CYAN, true);

            List<ElementCard> cards = new ArrayList<>();
            for (int i = 0; i < seq.size(); i++) {
                ElementCard c = new ElementCard(seq.get(i));
                c.popIn(i * 130);
                cards.add(c);
            }
            setStage(rowOf(cards));

            final long dur = (long) seqLen * diff.msPerItem + 1500L;
            final long start = System.currentTimeMillis();
            bar.setValue(1f);
            javax.swing.Timer t = new javax.swing.Timer(30, null);
            t.addActionListener(e -> {
                if (id != roundId) { t.stop(); return; }
                double f = (System.currentTimeMillis() - start) / (double) dur;
                bar.setValue((float) (1 - f));
                if (f >= 1) { t.stop(); showAnswer(); }
            });
            t.start();
        }

        private void showAnswer() {
            phase = Phase.ANSWER;
            roundId++;
            bar.setVisible(false);
            promptLabel.setText("What was the sequence?");
            promptLabel.setForeground(Theme.TEXT);
            subLabel.setText("Type each item  |  Enter = next slot  |  Backspace on empty = previous  |  or tap a chip");
            setPrimary("Submit  \u2713", Theme.GREEN, true);
            peekBtn.setVisible(true);
            peekBtn.setEnabled(!peeked);
            palette.setVisible(true);
            answerStart = System.currentTimeMillis();
            buildFields(null);
        }

        private void buildFields(List<String> prefill) {
            fields.clear();
            final int n = seq.size();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                final SlotField f = new SlotField();
                if (prefill != null) f.setText(prefill.get(i));
                f.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { focusIdx = idx; f.selectAll(); f.repaint(); }
                    @Override public void focusLost(FocusEvent e) { f.repaint(); }
                });
                f.addActionListener(e -> {
                    if (idx < n - 1) fields.get(idx + 1).requestFocusInWindow(); else submit();
                });
                f.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && f.getText().isEmpty() && idx > 0)
                            fields.get(idx - 1).requestFocusInWindow();
                    }
                });
                fields.add(f);
            }
            setStage(rowOf(fields));
            SwingUtilities.invokeLater(() -> { if (!fields.isEmpty()) fields.get(0).requestFocusInWindow(); });
        }

        private void fillFocused(String s) {
            if (phase != Phase.ANSWER || fields.isEmpty()) return;
            int i = Math.min(focusIdx, fields.size() - 1);
            fields.get(i).setText(s);
            fields.get(Math.min(i + 1, fields.size() - 1)).requestFocusInWindow();
        }

        private void peek() {
            if (phase != Phase.ANSWER || peeked) return;
            peeked = true;
            phase = Phase.PEEK;
            final int id = ++roundId;
            score = Math.max(0, score - 10);
            animateScore();
            peekBtn.setEnabled(false);
            setPrimary("Peeking...", Theme.DIM, false);
            final List<String> saved = new ArrayList<>();
            for (SlotField f : fields) saved.add(f.getText());
            promptLabel.setText("Quick peek!  (-10 points)");
            promptLabel.setForeground(Theme.AMBER);

            List<ElementCard> cards = new ArrayList<>();
            for (int i = 0; i < seq.size(); i++) {
                ElementCard c = new ElementCard(seq.get(i));
                c.popIn(i * 60);
                cards.add(c);
            }
            setStage(rowOf(cards));
            javax.swing.Timer t = new javax.swing.Timer(1300 + seq.size() * 80, e -> {
                if (id != roundId) return;
                phase = Phase.ANSWER;
                roundId++;
                promptLabel.setText("What was the sequence?");
                promptLabel.setForeground(Theme.TEXT);
                setPrimary("Submit  \u2713", Theme.GREEN, true);
                buildFields(saved);
            });
            t.setRepeats(false);
            t.start();
        }

        private void primaryAction() {
            switch (phase) {
                case MEMORIZE: roundId++; showAnswer(); break;
                case ANSWER: submit(); break;
                case FEEDBACK: startRound(); break;
                case OVER: finishGame(); break;
                default: break;
            }
        }

        private static String trunc(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "~"; }

        private void submit() {
            if (phase != Phase.ANSWER) return;
            phase = Phase.FEEDBACK;
            roundId++;

            List<String> in = new ArrayList<>();
            for (SlotField f : fields) in.add(f.getText());
            boolean[] ok = new SequencePattern(seq).check(in);
            int right = 0;
            for (boolean b : ok) if (b) right++;
            boolean all = right == seq.size();
            rounds++;
            double secs = (System.currentTimeMillis() - answerStart) / 1000.0;

            palette.setVisible(false);
            peekBtn.setVisible(false);

            List<ElementCard> review = new ArrayList<>();
            for (int i = 0; i < seq.size(); i++) {
                ElementCard c = new ElementCard(seq.get(i));
                c.setState(ok[i] ? 1 : 2);
                String got = in.get(i).trim();
                c.setSubtitle(ok[i] ? "correct" : (got.isEmpty() ? "(blank)" : "you: " + trunc(got, 6)));
                c.popIn(i * 70);
                review.add(c);
            }
            setStage(rowOf(review));

            if (all) {
                correctRounds++;
                streak++;
                bestStreakSession = Math.max(bestStreakSession, streak);
                longestSeq = Math.max(longestSeq, seq.size());
                int base = seq.size() * 10;
                int speed = (int) Math.max(0, Math.round(15 - secs));
                int streakBonus = Math.min(streak, 5) * 5;
                int gain = (int) Math.round((base + speed + streakBonus) * diff.mult);
                score += gain;
                boolean levelUp = streak % 3 == 0 && seqLen < MAX_LEN;
                if (levelUp) seqLen++;
                promptLabel.setText(PRAISE[rnd.nextInt(PRAISE.length)] + "  +" + gain);
                promptLabel.setForeground(Theme.GREEN);
                String detail = "Base " + base + "  +  Speed " + speed + "  +  Streak " + streakBonus + (diff.mult != 1.0 ? "   (x" + diff.mult + " " + diff.label + ")" : "");
                if (levelUp) { detail += "     LEVEL UP! Next: " + seqLen + " items"; burst(70); }
                else if (streak >= 2) burst(20);
                subLabel.setText(detail);
            } else {
                lives--;
                streak = 0;
                seqLen = Math.max(diff.startLen, seqLen - 1);
                promptLabel.setText(right == 0 ? "Oops - not this time" : "So close!  " + right + " / " + seq.size() + " correct");
                promptLabel.setForeground(Theme.RED);
                subLabel.setText(lives > 0 ? "Streak reset  |  " + lives + (lives == 1 ? " life" : " lives") + " left  |  check the correct answers below"
                                           : "No lives left - that's the game!");
                shake(stage);
            }

            animateScore();
            refreshHud();

            if (lives <= 0) { phase = Phase.OVER; setPrimary("See Results  >", Theme.PINK, true); }
            else setPrimary("Next Round  >", Theme.CYAN, true);
        }

        private void commit() {
            if (committed || rounds == 0) return;
            committed = true;
            player.gamesPlayed++;
            if (score > player.highScore) player.highScore = score;
            player.bestStreak = Math.max(player.bestStreak, bestStreakSession);
            Persistence.savePlayer(player);
        }

        private void finishGame() {
            ResultData d = new ResultData();
            d.score = score;
            d.newRecord = score > recordAtStart && score > 0;
            d.rounds = rounds;
            d.correctRounds = correctRounds;
            d.bestStreak = bestStreakSession;
            d.longestSeq = longestSeq;
            d.difficulty = diff;
            commit();
            d.highScore = player.highScore;
            parent.showResult(d);
        }
    }

    // =====================================================================
    //  RESULT / SCORE CARD
    // =====================================================================
    static class ResultPage extends AnimatedBackground {
        private static final String[] RANKS = {"Warming Up", "Sharp Rookie", "Pattern Pro", "Memory Master", "Mind Palace Legend"};
        private static final int[] THRESHOLDS = {0, 60, 200, 450, 900};
        private static final String[] RANK_MSG = {
            "Every champion starts somewhere - your brain just got its first workout.",
            "Nice start! You're building real recall muscle.",
            "Solid focus. Your pattern memory is clearly above average.",
            "Seriously impressive. Few people hold this much in their head.",
            "Absolutely elite recall. Your memory is a vault."
        };

        private final GameFrame parent;
        private final JLabel rankLabel = label(" ", Font.BOLD, 34f, Theme.TEXT);
        private final JLabel diffLabel = label(" ", Font.BOLD, 13f, Theme.MUTED);
        private final JLabel scoreLabel = label("0", Font.BOLD, 76f, Theme.CYAN);
        private final JLabel badge = label("*  NEW HIGH SCORE  *", Font.BOLD, 18f, Theme.AMBER);
        private final JLabel bestLabel = label(" ", Font.PLAIN, 14f, Theme.MUTED);
        private final JLabel msgLabel = label(" ", Font.PLAIN, 15f, Theme.TEXT);
        private final StarsPanel stars = new StarsPanel();
        private final StatBox roundsBox = new StatBox("ROUNDS", Theme.TEXT);
        private final StatBox accBox = new StatBox("ACCURACY", Theme.GREEN);
        private final StatBox streakBox = new StatBox("BEST STREAK", Theme.AMBER);
        private final StatBox lenBox = new StatBox("LONGEST SEQ", Theme.CYAN);
        private javax.swing.Timer countTimer, starTimer;
        private boolean blink;

        ResultPage(GameFrame parent) {
            this.parent = parent;
            setLayout(new GridBagLayout());

            GlassPanel card = new GlassPanel(new BorderLayout());
            card.setBorder(BorderFactory.createEmptyBorder(24, 44, 26, 44));
            JPanel col = vbox();
            col.add(label("GAME OVER", Font.BOLD, 13f, Theme.MUTED));
            col.add(Box.createVerticalStrut(2));
            col.add(rankLabel);
            col.add(diffLabel);
            col.add(Box.createVerticalStrut(8));
            col.add(stars);
            col.add(Box.createVerticalStrut(2));
            col.add(scoreLabel);
            col.add(label("POINTS", Font.BOLD, 12f, Theme.MUTED));
            col.add(Box.createVerticalStrut(6));
            col.add(badge);
            col.add(bestLabel);
            col.add(Box.createVerticalStrut(14));

            JPanel tiles = clear(new GridLayout(1, 4, 12, 0));
            for (StatBox b : new StatBox[]{roundsBox, accBox, streakBox, lenBox}) {
                GlassPanel t = new GlassPanel(new BorderLayout());
                t.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
                t.add(b, BorderLayout.CENTER);
                tiles.add(t);
            }
            tiles.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(tiles);
            col.add(Box.createVerticalStrut(16));
            col.add(msgLabel);
            col.add(Box.createVerticalStrut(20));

            JPanel btns = clear(new FlowLayout(FlowLayout.CENTER, 16, 0));
            RoundButton again = new RoundButton("Play Again", Theme.PINK, false);
            RoundButton menu = new RoundButton("Main Menu", Theme.VIOLET, false);
            again.addActionListener(e -> parent.startGame());
            menu.addActionListener(e -> parent.showMenu());
            btns.add(again);
            btns.add(menu);
            btns.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(btns);

            card.add(col, BorderLayout.CENTER);
            add(card);
            this.parent.getClass(); // keep reference

            javax.swing.Timer blinker = new javax.swing.Timer(450, e -> {
                if (!isShowing() || !badge.isVisible()) return;
                blink = !blink;
                badge.setForeground(blink ? Theme.PINK : Theme.AMBER);
            });
            blinker.start();
        }

        void present(ResultData d) {
            int tier = 0;
            for (int i = 0; i < THRESHOLDS.length; i++) if (d.score >= THRESHOLDS[i]) tier = i;
            int acc = d.rounds == 0 ? 0 : Math.round(100f * d.correctRounds / d.rounds);

            rankLabel.setText(RANKS[tier]);
            rankLabel.setForeground(tier >= 3 ? Theme.AMBER : tier == 2 ? Theme.CYAN : Theme.TEXT);
            diffLabel.setText(d.difficulty.label.toUpperCase() + " MODE");
            diffLabel.setForeground(d.difficulty.color);
            badge.setVisible(d.newRecord);
            bestLabel.setText(d.newRecord ? "You beat your previous best!" : "Personal best: " + d.highScore + "  (" + Math.max(0, d.highScore - d.score) + " away)");
            roundsBox.set(String.valueOf(d.rounds));
            accBox.set(acc + "%");
            streakBox.set("x" + d.bestStreak);
            lenBox.set(String.valueOf(d.longestSeq));

            String tip;
            if (d.rounds <= 1) tip = "Tip: take your time on the first sequences, then speed up.";
            else if (acc < 50) tip = "Tip: chunk items together or invent a tiny story - \"Red 42 @\" sticks better as one picture.";
            else if (d.longestSeq >= 7) tip = "Holding " + d.longestSeq + "+ items at once is genuinely strong working memory.";
            else if (d.bestStreak >= 3) tip = "Great streaks! Keep it going to unlock longer sequences.";
            else tip = "Try the Peek button sparingly and watch the countdown bar - speed earns bonus points.";
            msgLabel.setText("<html><div style='text-align:center;width:520px'>" + RANK_MSG[tier] + "<br><span style='color:#b4acdc'>" + tip + "</span></div></html>");

            // animations
            if (countTimer != null) countTimer.stop();
            if (starTimer != null) starTimer.stop();
            scoreLabel.setText("0");
            final int target = d.score;
            final int[] shown = {0};
            countTimer = new javax.swing.Timer(18, null);
            countTimer.addActionListener(e -> {
                shown[0] += Math.max(1, (target - shown[0]) / 10);
                if (shown[0] >= target) { shown[0] = target; countTimer.stop(); }
                scoreLabel.setText(String.valueOf(shown[0]));
            });
            countTimer.start();

            final int litTarget = tier + 1;
            final int[] lit = {0};
            stars.setLit(0);
            starTimer = new javax.swing.Timer(280, null);
            starTimer.addActionListener(e -> {
                lit[0]++;
                stars.setLit(lit[0]);
                if (lit[0] >= litTarget) starTimer.stop();
            });
            starTimer.setInitialDelay(500);
            starTimer.start();

            if (d.newRecord) burst(160);
            else if (tier >= 2) burst(70);
        }
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Player p = Persistence.loadPlayer();
            GameFrame gf = new GameFrame(p);
            gf.setVisible(true);
        });
    }
}