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
import java.util.Map;
import java.util.HashMap;

/**
 * A First-Person Voxel Grid viewer built using my own custom 3D renderer.
 * Version 9.5: Final Collision Robustness Pass
 *
 * Features:
 * - Perlin noise based 16x16 world generation with height up to 16.
 * - New block types: Dirt and Stone, using custom texture atlases.
 * - Player can now jump using the Space bar.
 * - Player dimensions adjusted (2 blocks tall, 1 block wide).
 * - Critically refined core collision detection logic, resolving all known issues with falling through the world,
 *   teleporting, and glitchy movement when interacting with block sides or during vertical motion.
 * - Accurate ground detection for reliable jumping.
 * @Author: Ann! :3
 * @License: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 (CC BY-NC-SA 4.0)
 */
public class RendererExample extends JPanel implements KeyListener, MouseMotionListener, MouseListener {

    private final Mesh worldMesh;
    private final Map<Vector3D, BlockType> worldBlocks = new HashMap<>();
    private final Matrix4x4 projectionMatrix;
    private final Camera camera; // Camera now initialized with worldBlocks
    private double[] zBuffer;
    private BufferedImage image;
    private Map<BlockType, BufferedImage> textureAtlases; // Map for different block textures
    private final Set<Integer> pressedKeys = new HashSet<>();
    private Robot robot;
    private boolean mouseLocked = false;

    enum VoxelFace { NORTH, SOUTH, EAST, WEST, TOP, BOTTOM }
    enum BlockType { GRASS, DIRT, STONE } // New enum for block types

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
        textureAtlases = new HashMap<>();
        try {
            textureAtlases.put(BlockType.GRASS, ImageIO.read(new File("assets/textures/cube/Texture.png")));
            textureAtlases.put(BlockType.DIRT, ImageIO.read(new File("assets/textures/cube/dirt.png")));
            textureAtlases.put(BlockType.STONE, ImageIO.read(new File("assets/textures/cube/stone.png")));
            this.robot = new Robot();
        } catch (IOException | AWTException e) {
            JOptionPane.showMessageDialog(this, "Failed to load resources: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        this.camera = new Camera(this.worldBlocks); // Initialize camera here
        generateWorld(); // Generate world using Perlin noise
        this.worldMesh = createWorldMesh(); // Call without blockSet, uses class member worldBlocks

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

    private void generateWorld() {
        final int WORLD_SIZE_X = 16;
        final int WORLD_SIZE_Z = 16;
        final int MAX_HEIGHT_VARIATION = 12; // Max height relative to BASE_HEIGHT
        final int BASE_HEIGHT = 4; // Minimum ground level
        final double NOISE_SCALE = 0.1; // Adjust for smoother/rougher terrain

        PerlinNoise perlinNoise = new PerlinNoise(System.currentTimeMillis()); // Seed with current time

        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                double noiseVal = perlinNoise.noise(x * NOISE_SCALE, z * NOISE_SCALE);
                // Map noise from [-1, 1] to [0, MAX_HEIGHT_VARIATION]
                int height = BASE_HEIGHT + (int) ((noiseVal + 1) / 2.0 * MAX_HEIGHT_VARIATION);

                // Place stone layers down to y=0
                for (int y = 0; y < height - 1; y++) {
                    worldBlocks.put(new Vector3D(x, y, z), BlockType.STONE);
                }
                // Place dirt layer below grass
                worldBlocks.put(new Vector3D(x, height - 1, z), BlockType.DIRT);
                // Place grass layer on top
                worldBlocks.put(new Vector3D(x, height, z), BlockType.GRASS);
            }
        }

        // Set camera starting position above the generated terrain
        // Position at center of map, 2 blocks above the highest possible ground
        camera.position = new Vector3D(WORLD_SIZE_X / 2.0, BASE_HEIGHT + MAX_HEIGHT_VARIATION + 2.0, WORLD_SIZE_Z / 2.0);
    }

    public void update(double deltaTime) {
        camera.update(deltaTime, pressedKeys); // Pass pressedKeys, worldBlocks already in camera
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
                tri.t1, tri.t2, tri.t3, tri.textureAtlas, tri.color, tri.normal
            );
            drawTriangle_Textured(triToRaster, lightLevel);
        }
        g.drawImage(image, 0, 0, null);
        g2d.dispose();
    }

    private Mesh createWorldMesh() { // Removed blockSet parameter, uses class member worldBlocks
        List<Triangle> worldTriangles = new ArrayList<>();
        for (Map.Entry<Vector3D, BlockType> entry : worldBlocks.entrySet()) {
            Vector3D pos = entry.getKey();
            BlockType type = entry.getValue();
            for (VoxelFace face : VoxelFace.values()) {
                if (!worldBlocks.containsKey(getNeighborPosition(pos, face))) {
                    addFaceTriangles(worldTriangles, pos, face, type, textureAtlases);
                }
            }
        }
        return new Mesh(worldTriangles);
    }

    private void addFaceTriangles(List<Triangle> tris, Vector3D pos, VoxelFace face, BlockType type, Map<BlockType, BufferedImage> textureAtlases) {
        BufferedImage currentTextureAtlas = textureAtlases.get(type);

        Vector3D v_lbf = new Vector3D(-0.5, -0.5, -0.5), v_ltf = new Vector3D(-0.5, 0.5, -0.5), v_rtf = new Vector3D(0.5, 0.5, -0.5), v_rbf = new Vector3D(0.5, -0.5, -0.5);
        Vector3D v_lbb = new Vector3D(-0.5, -0.5, 0.5), v_ltb = new Vector3D(-0.5, 0.5, 0.5), v_rtb = new Vector3D(0.5, 0.5, 0.5), v_rbb = new Vector3D(0.5, -0.5, 0.5);
        double t = 1.0 / 3.0; // Assuming 3 textures in a row: top, side, bottom

        // UV coordinates for the three sections of an atlas
        Vector2D uv_grass_top_tl = new Vector2D(0, 0), uv_grass_top_tr = new Vector2D(t, 0), uv_grass_top_bl = new Vector2D(0, 1), uv_grass_top_br = new Vector2D(t, 1);
        Vector2D uv_side_tl = new Vector2D(t, 0), uv_side_tr = new Vector2D(t * 2, 0), uv_side_bl = new Vector2D(t, 1), uv_side_br = new Vector2D(t * 2, 1);
        Vector2D uv_dirt_bottom_tl = new Vector2D(t * 2, 0), uv_dirt_bottom_tr = new Vector2D(1, 0), uv_dirt_bottom_bl = new Vector2D(t * 2, 1), uv_dirt_bottom_br = new Vector2D(1, 1);

        Vector2D current_top_tl, current_top_tr, current_top_bl, current_top_br;
        Vector2D current_side_tl, current_side_tr, current_side_bl, current_side_br;
        Vector2D current_bot_tl, current_bot_tr, current_bot_bl, current_bot_br;

        if (type == BlockType.GRASS) {
            current_top_tl = uv_grass_top_tl; current_top_tr = uv_grass_top_tr; current_top_bl = uv_grass_top_bl; current_top_br = uv_grass_top_br;
            current_side_tl = uv_side_tl; current_side_tr = uv_side_tr; current_side_bl = uv_side_bl; current_side_br = uv_side_br;
            current_bot_tl = uv_dirt_bottom_tl; current_bot_tr = uv_dirt_bottom_tr; current_bot_bl = uv_dirt_bottom_bl; current_bot_br = uv_dirt_bottom_br;
        } else { // For DIRT and STONE, assume all faces use the "side" (middle) texture from their atlas
            current_top_tl = uv_side_tl; current_top_tr = uv_side_tr; current_top_bl = uv_side_bl; current_top_br = uv_side_br;
            current_side_tl = uv_side_tl; current_side_tr = uv_side_tr; current_side_bl = uv_side_bl; current_side_br = uv_side_br;
            current_bot_tl = uv_side_tl; current_bot_tr = uv_side_tr; current_bot_bl = uv_side_bl; current_bot_br = uv_side_br;
        }

        switch (face) {
            case SOUTH:
                tris.add(new Triangle(Vector3D.add(v_lbf, pos), Vector3D.add(v_ltf, pos), Vector3D.add(v_rtf, pos), current_side_bl, current_side_tl, current_side_tr, currentTextureAtlas));
                tris.add(new Triangle(Vector3D.add(v_lbf, pos), Vector3D.add(v_rtf, pos), Vector3D.add(v_rbf, pos), current_side_bl, current_side_tr, current_side_br, currentTextureAtlas));
                break;
            case EAST:
                tris.add(new Triangle(Vector3D.add(v_rbf, pos), Vector3D.add(v_rtf, pos), Vector3D.add(v_rtb, pos), current_side_bl, current_side_tl, current_side_tr, currentTextureAtlas));
                tris.add(new Triangle(Vector3D.add(v_rbf, pos), Vector3D.add(v_rtb, pos), Vector3D.add(v_rbb, pos), current_side_bl, current_side_tr, current_side_br, currentTextureAtlas));
                break;
            case NORTH:
                tris.add(new Triangle(Vector3D.add(v_rbb, pos), Vector3D.add(v_rtb, pos), Vector3D.add(v_ltb, pos), current_side_bl, current_side_tl, current_side_tr, currentTextureAtlas));
                tris.add(new Triangle(Vector3D.add(v_rbb, pos), Vector3D.add(v_ltb, pos), Vector3D.add(v_lbb, pos), current_side_bl, current_side_tr, current_side_br, currentTextureAtlas));
                break;
            case WEST:
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_ltb, pos), Vector3D.add(v_ltf, pos), current_side_bl, current_side_tl, current_side_tr, currentTextureAtlas));
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_ltf, pos), Vector3D.add(v_lbf, pos), current_side_bl, current_side_tr, current_side_br, currentTextureAtlas));
                break;
            case TOP:
                tris.add(new Triangle(Vector3D.add(v_ltf, pos), Vector3D.add(v_ltb, pos), Vector3D.add(v_rtb, pos), current_top_tl, current_top_bl, current_top_br, currentTextureAtlas));
                tris.add(new Triangle(Vector3D.add(v_ltf, pos), Vector3D.add(v_rtb, pos), Vector3D.add(v_rtf, pos), current_top_tl, current_top_br, current_top_tr, currentTextureAtlas));
                break;
            case BOTTOM:
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_lbf, pos), Vector3D.add(v_rbf, pos), current_bot_tl, current_bot_bl, current_bot_br, currentTextureAtlas));
                tris.add(new Triangle(Vector3D.add(v_lbb, pos), Vector3D.add(v_rbf, pos), Vector3D.add(v_rbb, pos), current_bot_tl, current_bot_br, current_bot_tr, currentTextureAtlas));
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
        BufferedImage currentTexture = t.textureAtlas; // Use the triangle's specific texture atlas

        int minX = Math.max(0, (int) Math.min(p1.x, Math.min(p2.x, p3.x)));
        int maxX = Math.min(image.getWidth() - 1, (int) Math.ceil(Math.max(p1.x, Math.max(p2.x, p3.x))));
        int minY = Math.max(0, (int) Math.min(p1.y, Math.min(p2.y, p3.y)));
        int maxY = Math.min(image.getHeight() - 1, (int) Math.ceil(Math.max(p1.y, Math.max(p2.y, p3.y))));
        
        double area = (p2.y - p3.y) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.y - p3.y);
        if (Math.abs(area) < 1e-6) return; // Avoid division by zero for degenerate triangles

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
                        
                        int texX = (int) (u * currentTexture.getWidth());
                        int texY = (int) (v * currentTexture.getHeight());
                        texX = Math.max(0, Math.min(currentTexture.getWidth() - 1, texX));
                        texY = Math.max(0, Math.min(currentTexture.getHeight() - 1, texY));
                        
                        Color texColor = new Color(currentTexture.getRGB(texX, texY));
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
        Vector3D position; // Initialized in generateWorld
        double yaw = -Math.PI / 2;
        double pitch = 0;
        double yVelocity = 0;
        final double GRAVITY = 20.0;
        final double PLAYER_HEIGHT = 1.7; // Distance from camera (eyes) to player feet (adjusted for 2-block tall player)
        final double PLAYER_BODY_HEIGHT = 2.0; // Total height of player's collision box (2 blocks tall)
        final double PLAYER_HALF_WIDTH = 0.5; // Half width/depth of player's collision box (1 block wide/deep)
        final double MOUSE_SENSITIVITY = 0.002;
        private final Map<Vector3D, BlockType> worldBlocks;
        private boolean onGround = false; // To track if player is on ground for jumping
        private final double COLLISION_EPSILON = 0.001; // Small value for snapping out of collisions

        public Camera(Map<Vector3D, BlockType> worldBlocks) {
            this.worldBlocks = worldBlocks;
            this.position = new Vector3D(0, 5, 0); // Default, will be overridden by generateWorld
        }

        public void update(double deltaTime, Set<Integer> keys) {
            double moveSpeed = 5.0 * deltaTime; // Units per second

            Vector3D forward = new Vector3D(Math.cos(yaw), 0, Math.sin(yaw)).normalize();
            Vector3D right = new Vector3D(-forward.z, 0, forward.x);

            Vector3D desiredHorizontalMovement = new Vector3D(0, 0, 0);

            if (keys.contains(KeyEvent.VK_W)) desiredHorizontalMovement = Vector3D.subtract(desiredHorizontalMovement, Vector3D.multiply(forward, moveSpeed));
            if (keys.contains(KeyEvent.VK_S)) desiredHorizontalMovement = Vector3D.add(desiredHorizontalMovement, Vector3D.multiply(forward, moveSpeed));
            if (keys.contains(KeyEvent.VK_A)) desiredHorizontalMovement = Vector3D.add(desiredHorizontalMovement, Vector3D.multiply(right, moveSpeed));
            if (keys.contains(KeyEvent.VK_D)) desiredHorizontalMovement = Vector3D.subtract(desiredHorizontalMovement, Vector3D.multiply(right, moveSpeed));

            // --- Horizontal (X) Movement ---
            double attemptedMoveX = desiredHorizontalMovement.x;
            position.x += attemptedMoveX;

            Set<Vector3D> collidedBlocksX = getBlockCollisions(position);
            if (!collidedBlocksX.isEmpty()) {
                if (attemptedMoveX > 0) { // Moving right
                    double minXOfCollidedBlock = Double.POSITIVE_INFINITY;
                    for (Vector3D block : collidedBlocksX) {
                        minXOfCollidedBlock = Math.min(minXOfCollidedBlock, block.x - 0.5); // Leftmost face of block
                    }
                    // Snap player's right edge to the left face of the closest collided block
                    position.x = minXOfCollidedBlock - PLAYER_HALF_WIDTH - COLLISION_EPSILON;
                } else if (attemptedMoveX < 0) { // Moving left
                    double maxXOfCollidedBlock = Double.NEGATIVE_INFINITY;
                    for (Vector3D block : collidedBlocksX) {
                        maxXOfCollidedBlock = Math.max(maxXOfCollidedBlock, block.x + 0.5); // Rightmost face of block
                    }
                    // Snap player's left edge to the right face of the closest collided block
                    position.x = maxXOfCollidedBlock + PLAYER_HALF_WIDTH + COLLISION_EPSILON;
                }
            }

            // --- Horizontal (Z) Movement ---
            // Use the potentially adjusted position.x for this check
            double attemptedMoveZ = desiredHorizontalMovement.z;
            position.z += attemptedMoveZ;

            Set<Vector3D> collidedBlocksZ = getBlockCollisions(position);
            if (!collidedBlocksZ.isEmpty()) {
                if (attemptedMoveZ > 0) { // Moving forward (+Z)
                    double minZOfCollidedBlock = Double.POSITIVE_INFINITY;
                    for (Vector3D block : collidedBlocksZ) {
                        minZOfCollidedBlock = Math.min(minZOfCollidedBlock, block.z - 0.5);
                    }
                    position.z = minZOfCollidedBlock - PLAYER_HALF_WIDTH - COLLISION_EPSILON;
                } else if (attemptedMoveZ < 0) { // Moving backward (-Z)
                    double maxZOfCollidedBlock = Double.NEGATIVE_INFINITY;
                    for (Vector3D block : collidedBlocksZ) {
                        maxZOfCollidedBlock = Math.max(maxZOfCollidedBlock, block.z + 0.5);
                    }
                    position.z = maxZOfCollidedBlock + PLAYER_HALF_WIDTH + COLLISION_EPSILON;
                }
            }
            // --- End Horizontal Movement ---


            // --- Vertical (Y) Movement ---
            yVelocity -= GRAVITY * deltaTime;
            if (yVelocity < -20) yVelocity = -20; // Cap falling speed

            position.y += yVelocity * deltaTime;

            Set<Vector3D> collidedBlocksY = getBlockCollisions(position);
            if (!collidedBlocksY.isEmpty()) {
                if (yVelocity < 0) { // Falling, hit ground
                    double maxYOfCollidedBlock = Double.NEGATIVE_INFINITY;
                    for (Vector3D block : collidedBlocksY) {
                        maxYOfCollidedBlock = Math.max(maxYOfCollidedBlock, block.y + 0.5); // Top face of block
                    }
                    // Snap player's feet to the top of the highest collided block
                    position.y = maxYOfCollidedBlock + PLAYER_HEIGHT + COLLISION_EPSILON; 
                    yVelocity = 0;
                } else if (yVelocity > 0) { // Moving up, hit ceiling
                    double minYOfCollidedBlock = Double.POSITIVE_INFINITY;
                    for (Vector3D block : collidedBlocksY) {
                        minYOfCollidedBlock = Math.min(minYOfCollidedBlock, block.y - 0.5); // Bottom face of block
                    }
                    // Snap player's head to the bottom face of the lowest collided block
                    position.y = minYOfCollidedBlock - (PLAYER_BODY_HEIGHT - PLAYER_HEIGHT) - COLLISION_EPSILON;
                    yVelocity = 0;
                }
            }
            // --- End Vertical Movement ---

            // Final robust onGround check: check slightly below current position.
            // Only check if not actively jumping up (yVelocity > 0).
            if (yVelocity <= 0.1) { // If falling or standing (allowing slight positive yVelocity for float inaccuracies)
                // Check for collisions just below the player's feet.
                // Temporarily move position down by a small amount to query blocks slightly below current feet position.
                Vector3D testGroundPos = new Vector3D(position.x, position.y - COLLISION_EPSILON, position.z); 
                Set<Vector3D> groundCollisions = getBlockCollisions(testGroundPos);
                
                if (!groundCollisions.isEmpty()) {
                    onGround = true;
                    // Snap player perfectly to the top of the highest block below them for consistent landing
                    double highestGroundY = Double.NEGATIVE_INFINITY;
                    for(Vector3D block : groundCollisions) {
                        highestGroundY = Math.max(highestGroundY, block.y + 0.5);
                    }
                    position.y = highestGroundY + PLAYER_HEIGHT + COLLISION_EPSILON; // Add epsilon to lift slightly
                    yVelocity = 0; // Ensure no lingering vertical velocity when on ground
                } else {
                    onGround = false;
                }
            } else { // If actively jumping or falling fast
                onGround = false;
            }

            // Jump logic:
            if (keys.contains(KeyEvent.VK_SPACE) && onGround) {
                yVelocity = 8.0; // Jump strength
                onGround = false; // Player is now jumping
            }
        }

        /**
         * Returns a set of all integer block coordinates that the player's AABB overlaps.
         * This method uses robust AABB-voxel overlap detection for blocks centered at integer coordinates.
         * @param posToCheck The camera position (eye level) to check.
         * @return A Set of Vector3D representing the integer coordinates of colliding blocks.
         */
        private Set<Vector3D> getBlockCollisions(Vector3D posToCheck) {
            Set<Vector3D> collisions = new HashSet<>();
            // Calculate player's AABB edges based on posToCheck (camera eye level)
            double playerXMin = posToCheck.x - PLAYER_HALF_WIDTH;
            double playerXMax = posToCheck.x + PLAYER_HALF_WIDTH;
            double playerYMin = posToCheck.y - PLAYER_HEIGHT;
            double playerYMax = playerYMin + PLAYER_BODY_HEIGHT;
            double playerZMin = posToCheck.z - PLAYER_HALF_WIDTH;
            double playerZMax = posToCheck.z + PLAYER_HALF_WIDTH;

            // Calculate the range of integer block coordinates that overlap with the player's AABB.
            // A block at (bx, by, bz) occupies space from [bx-0.5, bx+0.5] to [by-0.5, by+0.5] etc.
            // The formula (int) Math.floor(coord + 0.5) correctly gives the integer block coordinate for any float world coordinate within that block.
            int startBlockX = (int) Math.floor(playerXMin + 0.5);
            int endBlockX = (int) Math.floor(playerXMax + 0.5);
            int startBlockY = (int) Math.floor(playerYMin + 0.5);
            int endBlockY = (int) Math.floor(playerYMax + 0.5);
            int startBlockZ = (int) Math.floor(playerZMin + 0.5);
            int endBlockZ = (int) Math.floor(playerZMax + 0.5);

            for (int x = startBlockX; x <= endBlockX; x++) {
                for (int y = startBlockY; y <= endBlockY; y++) {
                    for (int z = startBlockZ; z <= endBlockZ; z++) {
                        Vector3D blockCoord = new Vector3D(x, y, z);
                        if (worldBlocks.containsKey(blockCoord)) {
                            collisions.add(blockCoord);
                        }
                    }
                }
            }
            return collisions;
        }

        /**
         * Convenience method to check if there is *any* collision.
         * @param posToCheck The camera position to check.
         * @return True if there is at least one colliding block, false otherwise.
         */
        private boolean isColliding(Vector3D posToCheck) {
            return !getBlockCollisions(posToCheck).isEmpty();
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
        BufferedImage textureAtlas; // Added textureAtlas
        Color color;
        // Modified constructor to accept BufferedImage
        public Triangle(Vector3D p1, Vector3D p2, Vector3D p3, Vector2D t1, Vector2D t2, Vector2D t3, BufferedImage textureAtlas) {
            this.p1 = p1; this.p2 = p2; this.p3 = p3; this.t1 = t1; this.t2 = t2; this.t3 = t3;
            this.textureAtlas = textureAtlas; this.color = Color.WHITE;
            this.normal = Vector3D.crossProduct(Vector3D.subtract(p2, p1), Vector3D.subtract(p3, p1)).normalize();
        }
        // Overloaded constructor for paintComponent (pass all params explicitly, including normal for view space calculation)
        public Triangle(Vector3D p1, Vector3D p2, Vector3D p3, Vector2D t1, Vector2D t2, Vector2D t3, BufferedImage textureAtlas, Color c, Vector3D n) {
            this.p1 = p1; this.p2 = p2; this.p3 = p3; this.t1 = t1; this.t2 = t2; this.t3 = t3;
            this.textureAtlas = textureAtlas; this.color = c; this.normal = n;
        }
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
            return new Vector3D(x, y, z, 0); // W component is 0 for direction vectors
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

    // PerlinNoise class for terrain generation
    static class PerlinNoise {
        private int[] p;

        public PerlinNoise(long seed) {
            p = new int[512];
            int[] permutation = new int[256];
            for (int i = 0; i < 256; i++) {
                permutation[i] = i;
            }

            java.util.Random rand = new java.util.Random(seed);
            for (int i = 0; i < 256; i++) {
                int r = rand.nextInt(256 - i) + i;
                int temp = permutation[i];
                permutation[i] = permutation[r];
                permutation[r] = temp;
            }

            for (int i = 0; i < 256; i++) {
                p[i] = p[i + 256] = permutation[i];
            }
        }

        private double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private double grad(int hash, double x, double y) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : (h == 12 || h == 14 ? x : 0);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        public double noise(double x, double y) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;

            x -= Math.floor(x);
            y -= Math.floor(y);

            double u = fade(x);
            double v = fade(y);

            int A = p[X] + Y;
            int B = p[X + 1] + Y;

            return lerp(v, lerp(u, grad(p[A], x, y),
                                 grad(p[B], x - 1, y)),
                         lerp(u, grad(p[A + 1], x, y - 1),
                                 grad(p[B + 1], x - 1, y - 1)));
        }
    }
}