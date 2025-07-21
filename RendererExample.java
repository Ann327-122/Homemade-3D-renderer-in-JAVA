import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A First-Person Voxel Grid viewer built using my own custom 3D renderer.
 * Version 8.1: Final Look Correction
 *
 * All systems are now fully functional. YAYY!
 * Friends and enemies and those whom not fall under either, I present to you... "IM FINALLY THE RIGHT WAY ROUND update indev 8.1"
 * @Author: Ann! :3
 * @License: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 (CC BY-NC-SA 4.0)
 */
public class RendererExample extends JPanel implements KeyListener, MouseMotionListener, MouseListener {

    private final Mesh worldMesh;
    private final Set<Vector3D> blockSet = new HashSet<>();
    private final Matrix4x4 projectionMatrix;
    private final Camera camera = new Camera();
    private double[] zBuffer;
    private BufferedImage image;
    private BufferedImage texture;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private Robot robot;
    private boolean mouseLocked = false;

    enum VoxelFace { NORTH, SOUTH, EAST, WEST, TOP, BOTTOM }

    public static void main(String[] args) {
        JFrame frame = new JFrame("3D Renderer From Scratch Example");
        RendererExample panel = new RendererExample();
        frame.add(panel);
        frame.setSize(1280, 720);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                panel.requestFocusInWindow();
            }
        });
    }

    public RendererExample() {
        this.setBackground(Color.BLACK);
        try {
            this.texture = ImageIO.read(new File("assets/textures/cube/Texture.png"));
            this.robot = new Robot();
        } catch (IOException | AWTException e) {
            JOptionPane.showMessageDialog(this, "Failed to load resources: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        for (int x = -2; x < 2; x++) {
            for (int z = -2; z < 2; z++) {
                blockSet.add(new Vector3D(x, 0, z));
            }
        }
        this.worldMesh = createWorldMesh(blockSet);

        double aspectRatio = (double) 720 / 1280;
        this.projectionMatrix = Matrix4x4.createProjection(90.0, aspectRatio, 0.1, 1000.0);

        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.setFocusable(true);

        new Thread(() -> {
            long lastTime = System.nanoTime();
            while (true) {
                long now = System.nanoTime();
                double deltaTime = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;
                update(deltaTime);
                repaint();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void update(double deltaTime) {
        camera.update(deltaTime, pressedKeys, blockSet);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight()) {
            image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            zBuffer = new double[getWidth() * getHeight()];
            projectionMatrix.m[0][0] = (double) getHeight() / getWidth() * projectionMatrix.m[1][1];
        }

        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(135, 206, 235)); // Sky color
        g2d.fillRect(0, 0, getWidth(), getHeight());
        Arrays.fill(zBuffer, Double.POSITIVE_INFINITY);

        Matrix4x4 viewMatrix = camera.getViewMatrix();

        for (Triangle tri : worldMesh.tris) {
            Vector3D p1_view = Matrix4x4.multiply(viewMatrix, tri.p1);
            Vector3D p2_view = Matrix4x4.multiply(viewMatrix, tri.p2);
            Vector3D p3_view = Matrix4x4.multiply(viewMatrix, tri.p3);
            Vector3D normal_view = Matrix4x4.multiplyDirection(viewMatrix, tri.normal);

            if (Vector3D.dotProduct(normal_view, p1_view.normalize()) >= 0) {
                continue; // Back-face culling
            }

            Vector3D p1_projected = Matrix4x4.multiply(projectionMatrix, p1_view);
            Vector3D p2_projected = Matrix4x4.multiply(projectionMatrix, p2_view);
            Vector3D p3_projected = Matrix4x4.multiply(projectionMatrix, p3_view);

            if (p1_projected.w < 0.1 || p2_projected.w < 0.1 || p3_projected.w < 0.1) {
                continue; // Clipping
            }

            double lightLevel = Math.max(0.2, Vector3D.dotProduct(tri.normal, new Vector3D(0.5, 1.0, -0.5).normalize()));

            p1_projected.perspectiveDivide();
            p2_projected.perspectiveDivide();
            p3_projected.perspectiveDivide();

            // Convert from normalized device coordinates to screen coordinates
            p1_projected.x = (p1_projected.x + 1) * 0.5 * getWidth();
            p2_projected.x = (p2_projected.x + 1) * 0.5 * getWidth();
            p3_projected.x = (p3_projected.x + 1) * 0.5 * getWidth();

            // Invert Y-axis to fix upside-down rendering
            p1_projected.y = (1.0 - p1_projected.y) * 0.5 * getHeight();
            p2_projected.y = (1.0 - p2_projected.y) * 0.5 * getHeight();
            p3_projected.y = (1.0 - p3_projected.y) * 0.5 * getHeight();

            Triangle triToRaster = new Triangle(
                new Vector3D(p1_projected.x, p1_projected.y, p1_view.z),
                new Vector3D(p2_projected.x, p2_projected.y, p2_view.z),
                new Vector3D(p3_projected.x, p3_projected.y, p3_view.z),
                tri.t1, tri.t2, tri.t3, tri.color, tri.normal
            );
            drawTriangle_Textured(triToRaster, lightLevel);
        }
        g.drawImage(image, 0, 0, null);
        g2d.dispose();
    }

    private Mesh createWorldMesh(Set<Vector3D> blockSet) {
        List<Triangle> worldTriangles = new ArrayList<>();
        for (Vector3D pos : blockSet) {
            for (VoxelFace face : VoxelFace.values()) {
                if (!blockSet.contains(getNeighborPosition(pos, face))) {
                    addFaceTriangles(worldTriangles, pos, face);
                }
            }
        }
        return new Mesh(worldTriangles);
    }

    private void addFaceTriangles(List<Triangle> tris, Vector3D pos, VoxelFace face) {
        Vector3D v_lbf = new Vector3D(-0.5, -0.5, -0.5), v_ltf = new Vector3D(-0.5, 0.5, -0.5), v_rtf = new Vector3D(0.5, 0.5, -0.5), v_rbf = new Vector3D(0.5, -0.5, -0.5);
        Vector3D v_lbb = new Vector3D(-0.5, -0.5, 0.5), v_ltb = new Vector3D(-0.5, 0.5, 0.5), v_rtb = new Vector3D(0.5, 0.5, 0.5), v_rbb = new Vector3D(0.5, -0.5, 0.5);
        double t = 1.0 / 3.0;
        Vector2D uv_top_tl = new Vector2D(0, 0), uv_top_tr = new Vector2D(t, 0), uv_top_bl = new Vector2D(0, 1), uv_top_br = new Vector2D(t, 1);
        Vector2D uv_side_tl = new Vector2D(t, 0), uv_side_tr = new Vector2D(t * 2, 0), uv_side_bl = new Vector2D(t, 1), uv_side_br = new Vector2D(t * 2, 1);
        Vector2D uv_bot_tl = new Vector2D(t * 2, 0), uv_bot_tr = new Vector2D(1, 0), uv_bot_bl = new Vector2D(t * 2, 1), uv_bot_br = new Vector2D(1, 1);

        switch (face) {
            case SOUTH:
                tris.add(new Triangle(Vector3D.add(v_lbf, pos), Vector3D.add(v_ltf, pos), Vector3D.add(v_rtf, pos), uv_side_bl, uv_side_tl, uv_side_tr));
                tris.add(new Triangle(Vector3D.add(v_lbf, pos), Vector3D.add(v_rtf, pos), Vector3D.add(v_rbf, pos), uv_side_bl, uv_side_tr, uv_side_br));
                break;
            case EAST:
                tris.add(new Triangle(Vector3D.add(v_rbf, pos), Vector3D.add(v_rtf, pos), Vector3D.add(v_rtb, pos), uv_side_bl, uv_side_tl, uv_side_tr));
                tris.add(new Triangle(Vector3D.add(v_rbf, pos), Vector3D.add(v_rtb, pos), Vector3D.add(v_rbb, pos), uv_side_bl, uv_side_tr, uv_side_br));
                break;
            case NORTH:
                tris.add(new Triangle(Vector3D.add(v_rbb, pos), Vector3D.add(v_rtb, pos), Vector3D.add(v_ltb, pos), uv_side_bl, uv_side_tl, uv_side_tr));
                tris.add(new Triangle(Vector3D.add(v_rbb, pos), Vector3D.add(v_ltb, pos), Vector3D.add(v_lbb, pos), uv_side_bl, uv_side_tr, uv_side_br));
                break;
            case WEST:
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_ltb, pos), Vector3D.add(v_ltf, pos), uv_side_bl, uv_side_tl, uv_side_tr));
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_ltf, pos), Vector3D.add(v_lbf, pos), uv_side_bl, uv_side_tr, uv_side_br));
                break;
            case TOP:
                tris.add(new Triangle(Vector3D.add(v_ltf, pos), Vector3D.add(v_ltb, pos), Vector3D.add(v_rtb, pos), uv_top_tl, uv_top_bl, uv_top_br));
                tris.add(new Triangle(Vector3D.add(v_ltf, pos), Vector3D.add(v_rtb, pos), Vector3D.add(v_rtf, pos), uv_top_tl, uv_top_br, uv_top_tr));
                break;
            case BOTTOM:
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_lbf, pos), Vector3D.add(v_rbf, pos), uv_bot_tl, uv_bot_bl, uv_bot_br));
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_rbf, pos), Vector3D.add(v_rbb, pos), uv_bot_tl, uv_bot_br, uv_bot_tr));
                break;
        }
    }

    private Vector3D getNeighborPosition(Vector3D p, VoxelFace f) {
        switch (f) {
            case EAST: return new Vector3D(p.x + 1, p.y, p.z);
            case WEST: return new Vector3D(p.x - 1, p.y, p.z);
            case TOP: return new Vector3D(p.x, p.y + 1, p.z);
            case BOTTOM: return new Vector3D(p.x, p.y - 1, p.z);
            case NORTH: return new Vector3D(p.x, p.y, p.z + 1);
            case SOUTH: return new Vector3D(p.x, p.y, p.z - 1);
            default: return p;
        }
    }

    private void drawTriangle_Textured(Triangle t, double lightLevel) {
        Vector3D p1 = t.p1, p2 = t.p2, p3 = t.p3;
        Vector2D t1 = t.t1, t2 = t.t2, t3 = t.t3;

        int minX = Math.max(0, (int) Math.min(p1.x, Math.min(p2.x, p3.x)));
        int maxX = Math.min(image.getWidth() - 1, (int) Math.ceil(Math.max(p1.x, Math.max(p2.x, p3.x))));
        int minY = Math.max(0, (int) Math.min(p1.y, Math.min(p2.y, p3.y)));
        int maxY = Math.min(image.getHeight() - 1, (int) Math.ceil(Math.max(p1.y, Math.max(p2.y, p3.y))));
        
        double area = (p2.y - p3.y) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.y - p3.y);
        if (Math.abs(area) < 1e-6) return;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double w1 = ((p2.y - p3.y) * (x - p3.x) + (p3.x - p2.x) * (y - p3.y)) / area;
                double w2 = ((p3.y - p1.y) * (x - p3.x) + (p1.x - p3.x) * (y - p3.y)) / area;
                double w3 = 1.0 - w1 - w2;

                if (w1 >= 0 && w2 >= 0 && w3 >= 0) {
                    double z_persp_inv = w1 / p1.z + w2 / p2.z + w3 / p3.z;
                    double z = 1.0 / z_persp_inv;
                    int zBufferIndex = y * image.getWidth() + x;
                    
                    if (z < zBuffer[zBufferIndex]) {
                        double u = (w1 * t1.u / p1.z + w2 * t2.u / p2.z + w3 * t3.u / p3.z) * z;
                        double v = (w1 * t1.v / p1.z + w2 * t2.v / p2.z + w3 * t3.v / p3.z) * z;
                        
                        int texX = (int) (u * texture.getWidth());
                        int texY = (int) (v * texture.getHeight());
                        texX = Math.max(0, Math.min(texture.getWidth() - 1, texX));
                        texY = Math.max(0, Math.min(texture.getHeight() - 1, texY));
                        
                        Color texColor = new Color(texture.getRGB(texX, texY));
                        Color finalColor = new Color((int) (texColor.getRed() * lightLevel), (int) (texColor.getGreen() * lightLevel), (int) (texColor.getBlue() * lightLevel));
                        
                        image.setRGB(x, y, finalColor.getRGB());
                        zBuffer[zBufferIndex] = z;
                    }
                }
            }
        }
    }

    private void setMouseLock(boolean locked) {
        mouseLocked = locked;
        Cursor cursor = locked ? Toolkit.getDefaultToolkit().createCustomCursor(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank") : Cursor.getDefaultCursor();
        setCursor(cursor);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            setMouseLock(false);
        } else {
            pressedKeys.add(e.getKeyCode());
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) { if (!mouseLocked) setMouseLock(true); }
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (mouseLocked) {
            try {
                Point center = new Point(getLocationOnScreen().x + getWidth() / 2, getLocationOnScreen().y + getHeight() / 2);
                double dx = e.getXOnScreen() - center.x;
                double dy = e.getYOnScreen() - center.y;
                camera.updateLook(dx, dy);
                robot.mouseMove(center.x, center.y);
            } catch (Exception err) {
                // Ignore robot errors
            }
        }
    }

    // --- Inner Classes ---

    static class Camera {
        Vector3D position = new Vector3D(0, 5, 0);
        double yaw = -Math.PI / 2;
        double pitch = 0;
        double yVelocity = 0;
        final double GRAVITY = 20.0;
        final double PLAYER_HEIGHT = 1.6;
        final double MOUSE_SENSITIVITY = 0.002;

        public void update(double deltaTime, Set<Integer> keys, Set<Vector3D> world) {
            double moveSpeed = 5.0 * deltaTime;
            Vector3D forward = new Vector3D(Math.cos(yaw), 0, Math.sin(yaw)).normalize();
            Vector3D right = new Vector3D(-forward.z, 0, forward.x);

            if (keys.contains(KeyEvent.VK_S)) position = Vector3D.add(position, Vector3D.multiply(forward, moveSpeed));
            if (keys.contains(KeyEvent.VK_W)) position = Vector3D.subtract(position, Vector3D.multiply(forward, moveSpeed));
            if (keys.contains(KeyEvent.VK_D)) position = Vector3D.subtract(position, Vector3D.multiply(right, moveSpeed));
            if (keys.contains(KeyEvent.VK_A)) position = Vector3D.add(position, Vector3D.multiply(right, moveSpeed));

            yVelocity -= GRAVITY * deltaTime;
            position.y += yVelocity * deltaTime;
            Vector3D feet = new Vector3D(Math.round(position.x), Math.floor(position.y - PLAYER_HEIGHT), Math.round(position.z));
            if (world.contains(feet)) {
                double groundLevel = feet.y + 0.5;
                if (position.y - PLAYER_HEIGHT < groundLevel) {
                    yVelocity = 0;
                    position.y = groundLevel + PLAYER_HEIGHT;
                }
            }
        }

        public void updateLook(double dx, double dy) {
            yaw += dx * MOUSE_SENSITIVITY;
            pitch += dy * MOUSE_SENSITIVITY;
            pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch)); // Clamp pitch
        }

        public Matrix4x4 getViewMatrix() {
            Vector3D forwardDirection = new Vector3D(
                Math.cos(yaw) * Math.cos(pitch),
                Math.sin(pitch),
                Math.sin(yaw) * Math.cos(pitch)
            );
            Vector3D target = Vector3D.add(position, forwardDirection);
            Vector3D worldUp = new Vector3D(0, 1, 0);
            return Matrix4x4.createLookAt(position, target, worldUp);
        }
    }

    static class Vector2D {
        double u, v;
        public Vector2D(double u, double v) { this.u = u; this.v = v; }
    }

    static class Vector3D {
        double x, y, z, w;
        public Vector3D(double x, double y, double z) { this.x = x; this.y = y; this.z = z; this.w = 1; }
        public Vector3D(double x, double y, double z, double w) { this.x = x; this.y = y; this.z = z; this.w = w; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Vector3D v = (Vector3D) o; return Double.compare(v.x, x) == 0 && Double.compare(v.y, y) == 0 && Double.compare(v.z, z) == 0; }
        @Override public int hashCode() { return java.util.Objects.hash(x, y, z); }
        public void perspectiveDivide() { if (w != 0) { x /= w; y /= w; z /= w; } }
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
        public Vector3D normalize() { double l = length(); return l == 0 ? new Vector3D(0, 0, 0) : new Vector3D(x / l, y / l, z / l); }
        public static Vector3D add(Vector3D v1, Vector3D v2) { return new Vector3D(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z); }
        public static Vector3D subtract(Vector3D v1, Vector3D v2) { return new Vector3D(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z); }
        public static Vector3D multiply(Vector3D v, double s) { return new Vector3D(v.x * s, v.y * s, v.z * s); }
        public static double dotProduct(Vector3D v1, Vector3D v2) { return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z; }
        public static Vector3D crossProduct(Vector3D v1, Vector3D v2) { return new Vector3D(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z, v1.x * v2.y - v1.y * v2.x); }
    }

    static class Triangle {
        Vector3D p1, p2, p3, normal;
        Vector2D t1, t2, t3;
        Color color;
        public Triangle(Vector3D p1, Vector3D p2, Vector3D p3, Vector2D t1, Vector2D t2, Vector2D t3) { this.p1 = p1; this.p2 = p2; this.p3 = p3; this.t1 = t1; this.t2 = t2; this.t3 = t3; this.color = Color.WHITE; this.normal = Vector3D.crossProduct(Vector3D.subtract(p2, p1), Vector3D.subtract(p3, p1)).normalize(); }
        public Triangle(Vector3D p1, Vector3D p2, Vector3D p3, Vector2D t1, Vector2D t2, Vector2D t3, Color c, Vector3D n) { this.p1 = p1; this.p2 = p2; this.p3 = p3; this.t1 = t1; this.t2 = t2; this.t3 = t3; this.color = c; this.normal = n; }
    }

    static class Mesh {
        List<Triangle> tris;
        public Mesh(List<Triangle> tris) { this.tris = tris; }
    }

    static class Matrix4x4 {
        double[][] m = new double[4][4];
        public static Vector3D multiply(Matrix4x4 matrix, Vector3D vector) {
            double x = vector.x * matrix.m[0][0] + vector.y * matrix.m[1][0] + vector.z * matrix.m[2][0] + vector.w * matrix.m[3][0];
            double y = vector.x * matrix.m[0][1] + vector.y * matrix.m[1][1] + vector.z * matrix.m[2][1] + vector.w * matrix.m[3][1];
            double z = vector.x * matrix.m[0][2] + vector.y * matrix.m[1][2] + vector.z * matrix.m[2][2] + vector.w * matrix.m[3][2];
            double w = vector.x * matrix.m[0][3] + vector.y * matrix.m[1][3] + vector.z * matrix.m[2][3] + vector.w * matrix.m[3][3];
            return new Vector3D(x, y, z, w);
        }
        public static Vector3D multiplyDirection(Matrix4x4 matrix, Vector3D vector) {
            double x = vector.x * matrix.m[0][0] + vector.y * matrix.m[1][0] + vector.z * matrix.m[2][0];
            double y = vector.x * matrix.m[0][1] + vector.y * matrix.m[1][1] + vector.z * matrix.m[2][1];
            double z = vector.x * matrix.m[0][2] + vector.y * matrix.m[1][2] + vector.z * matrix.m[2][2];
            return new Vector3D(x, y, z, 0);
        }
        public static Matrix4x4 createLookAt(Vector3D eye, Vector3D target, Vector3D up) {
            Vector3D zaxis = Vector3D.subtract(target, eye).normalize();
            Vector3D xaxis = Vector3D.crossProduct(up, zaxis).normalize();
            Vector3D yaxis = Vector3D.crossProduct(zaxis, xaxis);
            Matrix4x4 view = new Matrix4x4();
            view.m[0][0] = xaxis.x;  view.m[1][0] = xaxis.y;  view.m[2][0] = xaxis.z;  view.m[3][0] = -Vector3D.dotProduct(xaxis, eye);
            view.m[0][1] = yaxis.x;  view.m[1][1] = yaxis.y;  view.m[2][1] = yaxis.z;  view.m[3][1] = -Vector3D.dotProduct(yaxis, eye);
            view.m[0][2] = -zaxis.x; view.m[1][2] = -zaxis.y; view.m[2][2] = -zaxis.z; view.m[3][2] = Vector3D.dotProduct(zaxis, eye);
            view.m[3][3] = 1;
            return view;
        }
        public static Matrix4x4 createProjection(double fov, double aspectRatio, double near, double far) {
            Matrix4x4 p = new Matrix4x4();
            double fovRad = 1.0 / Math.tan(Math.toRadians(fov * 0.5));
            p.m[0][0] = aspectRatio * fovRad;
            p.m[1][1] = fovRad;
            p.m[2][2] = far / (far - near);
            p.m[3][2] = (-far * near) / (far - near);
            p.m[2][3] = 1.0;
            p.m[3][3] = 0.0;
            return p;
        }
    }
}