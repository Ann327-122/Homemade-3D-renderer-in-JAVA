import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

 /**
 * A shape and obj file renderer built in Java with no external packages (only uses built in imports).
 * Version 5: Interactive Controls
 * Renders a 3D model with textures. Rotation is controlled via the arrow keys.
 * sorry for lack of comments, I shall add more in later for easier usage. :)
 * JUST A NOTE, btw this was made first, before the 'RendererExample.java' file, which means the camera is technically Upside down, with the top and bottom faces swapped on the cube, and the side textures upside down, HOWEVER, you can't notice it really because I've made the controls line up correctly :) (the camera should be fixed in the 'RendererExample.java' file tho :))
 * @Author: Ann! :3
 * @License: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 (CC BY-NC-SA 4.0)
 */
 
public class HomemadeRenderer extends JPanel implements KeyListener {

    // Hi there! set any one of these to true, and make sure the other two are false otherwise it will probs error.. I think...
    private boolean renderVoxel = true; //render a cube
    private boolean renderSphere = false; //render a sphere
    private boolean renderObject = false; //render the specified obj file

    
    private final Mesh meshToRender;
    private double rotationX = 0; 
    private double rotationY = 0; 
    private final Matrix4x4 projectionMatrix;

    
    private double[] zBuffer;
    private BufferedImage image;
    private BufferedImage texture;

    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Home made 3D Renderer - V5");
        HomemadeRenderer panel = new HomemadeRenderer();
        frame.add(panel);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        new Thread(() -> {
            while (true) {
                
                
                panel.repaint();
                try {
                    Thread.sleep(16); 
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public HomemadeRenderer() {
        this.setBackground(Color.BLACK);
        try {
            this.texture = ImageIO.read(new File("assets/textures/cube/Texture.png"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Texture file 'textures/cube/Texture.png' not found!", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        
        if (renderVoxel) this.meshToRender = createCube();
        else if (renderSphere) this.meshToRender = createSphere(1.2, 24, 18);
        else if (renderObject) this.meshToRender = loadFromObjFile("assets/Objects_(obj)/teapot.obj", new Color(170, 190, 255)); //specify obj file and path
        else this.meshToRender = createCube();
        
        this.projectionMatrix = Matrix4x4.createProjection(90.0, 1.0, 0.1, 1000.0);
        
        
        this.addKeyListener(this);
        this.setFocusable(true);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight()) {
            image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            zBuffer = new double[getWidth() * getHeight()];
        }
        Graphics2D imageGraphics = image.createGraphics();
        imageGraphics.setColor(Color.BLACK);
        imageGraphics.fillRect(0, 0, getWidth(), getHeight());
        Arrays.fill(zBuffer, Double.POSITIVE_INFINITY);

        projectionMatrix.m[0][0] = (double)getHeight() / getWidth() * projectionMatrix.m[1][1];
        
        
        Matrix4x4 rotX = Matrix4x4.createRotationX(rotationX);
        Matrix4x4 rotY = Matrix4x4.createRotationY(rotationY);
        Matrix4x4 transformMatrix = Matrix4x4.multiply(rotY, rotX);

        for (Triangle tri : meshToRender.tris) {
            Vector3D p1_rotated = Matrix4x4.multiply(transformMatrix, tri.p1);
            Vector3D p2_rotated = Matrix4x4.multiply(transformMatrix, tri.p2);
            Vector3D p3_rotated = Matrix4x4.multiply(transformMatrix, tri.p3);

            double distance = renderVoxel ? 3.0 : 5.0;
            p1_rotated.z += distance; p2_rotated.z += distance; p3_rotated.z += distance;

            Vector3D line1 = Vector3D.subtract(p2_rotated, p1_rotated);
            Vector3D line2 = Vector3D.subtract(p3_rotated, p1_rotated);
            Vector3D normal = Vector3D.crossProduct(line1, line2).normalize();
            Vector3D cameraRay = Vector3D.subtract(p1_rotated, new Vector3D(0, 0, 0));
            if (Vector3D.dotProduct(normal, cameraRay) >= 0) continue;

            Vector3D lightDirection = new Vector3D(0.5, -0.5, -1.0).normalize();
            double dp = Math.max(0.1, Vector3D.dotProduct(normal, lightDirection));

            Vector3D p1_projected = Matrix4x4.multiply(projectionMatrix, p1_rotated);
            Vector3D p2_projected = Matrix4x4.multiply(projectionMatrix, p2_rotated);
            Vector3D p3_projected = Matrix4x4.multiply(projectionMatrix, p3_rotated);
            p1_projected.perspectiveDivide(); p2_projected.perspectiveDivide(); p3_projected.perspectiveDivide();
            
            p1_projected.x = (p1_projected.x + 1.0) * 0.5 * getWidth(); p1_projected.y = (p1_projected.y + 1.0) * 0.5 * getHeight();
            p2_projected.x = (p2_projected.x + 1.0) * 0.5 * getWidth(); p2_projected.y = (p2_projected.y + 1.0) * 0.5 * getHeight();
            p3_projected.x = (p3_projected.x + 1.0) * 0.5 * getWidth(); p3_projected.y = (p3_projected.y + 1.0) * 0.5 * getHeight();
            
            Triangle triToRaster = new Triangle(
                new Vector3D(p1_projected.x, p1_projected.y, p1_rotated.z),
                new Vector3D(p2_projected.x, p2_projected.y, p2_rotated.z),
                new Vector3D(p3_projected.x, p3_projected.y, p3_rotated.z),
                tri.t1, tri.t2, tri.t3, tri.color
            );
            drawTriangle_Textured(triToRaster, dp);
        }
        g.drawImage(image, 0, 0, null);
        imageGraphics.dispose();
    }
    
    public void update() {}

    private void drawTriangle_Textured(Triangle tri, double lighting_dp) {
        Vector3D p1 = tri.p1; Vector3D p2 = tri.p2; Vector3D p3 = tri.p3;
        Vector2D t1 = tri.t1; Vector2D t2 = tri.t2; Vector2D t3 = tri.t3;
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
                    double z = 1.0 / (w1 / p1.z + w2 / p2.z + w3 / p3.z);
                    int zIndex = y * image.getWidth() + x;
                    if (z < zBuffer[zIndex]) {
                        Color finalColor;
                        if (texture != null && renderVoxel) {
                            double u = (w1 * t1.u / p1.z + w2 * t2.u / p2.z + w3 * t3.u / p3.z) * z;
                            double v = (w1 * t1.v / p1.z + w2 * t2.v / p2.z + w3 * t3.v / p3.z) * z;
                            int texX = (int) (u * texture.getWidth()), texY = (int) (v * texture.getHeight());
                            texX = Math.max(0, Math.min(texture.getWidth() - 1, texX));
                            texY = Math.max(0, Math.min(texture.getHeight() - 1, texY));
                            finalColor = new Color(texture.getRGB(texX, texY));
                            finalColor = new Color((int)(finalColor.getRed() * lighting_dp), (int)(finalColor.getGreen() * lighting_dp), (int)(finalColor.getBlue() * lighting_dp));
                        } else {
                            finalColor = new Color((int)(tri.color.getRed() * lighting_dp), (int)(tri.color.getGreen() * lighting_dp), (int)(tri.color.getBlue() * lighting_dp));
                        }
                        image.setRGB(x, y, finalColor.getRGB());
                        zBuffer[zIndex] = z;
                    }
                }
            }
        }
    }


private Mesh createCube() {
    Vector3D[] verts = {
        new Vector3D(-0.5, -0.5, -0.5), new Vector3D(-0.5, 0.5, -0.5), new Vector3D(0.5, 0.5, -0.5), new Vector3D(0.5, -0.5, -0.5),
        new Vector3D(-0.5, -0.5, 0.5), new Vector3D(-0.5, 0.5, 0.5), new Vector3D(0.5, 0.5, 0.5), new Vector3D(0.5, -0.5, 0.5)
    };
    
    
    
    double third = 1.0/3.0;
    Vector2D uv_top_bl = new Vector2D(0, 0),             uv_top_br = new Vector2D(third, 0);
    Vector2D uv_top_tl = new Vector2D(0, 1),             uv_top_tr = new Vector2D(third, 1);
    
    Vector2D uv_side_bl = new Vector2D(third, 0),        uv_side_br = new Vector2D(third*2, 0);
    Vector2D uv_side_tl = new Vector2D(third, 1),        uv_side_tr = new Vector2D(third*2, 1);
    
    Vector2D uv_bot_bl = new Vector2D(third*2, 0),      uv_bot_br = new Vector2D(1, 0);
    Vector2D uv_bot_tl = new Vector2D(third*2, 1),      uv_bot_tr = new Vector2D(1, 1);
    
    List<Triangle> tris = new ArrayList<>(Arrays.asList(
        
        new Triangle(verts[0], verts[1], verts[2], uv_side_bl, uv_side_tl, uv_side_tr, Color.WHITE),
        new Triangle(verts[0], verts[2], verts[3], uv_side_bl, uv_side_tr, uv_side_br, Color.WHITE),
        
        new Triangle(verts[3], verts[2], verts[6], uv_side_bl, uv_side_tl, uv_side_tr, Color.WHITE),
        new Triangle(verts[3], verts[6], verts[7], uv_side_bl, uv_side_tr, uv_side_br, Color.WHITE),
        
        new Triangle(verts[7], verts[6], verts[5], uv_side_bl, uv_side_tl, uv_side_tr, Color.WHITE),
        new Triangle(verts[7], verts[5], verts[4], uv_side_bl, uv_side_tr, uv_side_br, Color.WHITE),
        
        new Triangle(verts[4], verts[5], verts[1], uv_side_bl, uv_side_tl, uv_side_tr, Color.WHITE),
        new Triangle(verts[4], verts[1], verts[0], uv_side_bl, uv_side_tr, uv_side_br, Color.WHITE),
        
        
        
        new Triangle(verts[1], verts[5], verts[6], uv_bot_bl, uv_bot_tl, uv_bot_tr, Color.WHITE),
        new Triangle(verts[1], verts[6], verts[2], uv_bot_bl, uv_bot_tr, uv_bot_br, Color.WHITE),
        
        new Triangle(verts[4], verts[0], verts[3], uv_top_bl, uv_top_tl, uv_top_tr, Color.WHITE),
        new Triangle(verts[4], verts[3], verts[7], uv_top_bl, uv_top_tr, uv_top_br, Color.WHITE)
    ));
    
    return new Mesh(tris);
}
    private Mesh createSphere(double radius, int sectors, int stacks) {
        List<Vector3D> vertices = new ArrayList<>();
        for (int i = 0; i <= stacks; i++) {
            double stackAngle = Math.PI / 2 - i * Math.PI / stacks;
            double xy = radius * Math.cos(stackAngle);
            double z = radius * Math.sin(stackAngle);
            for (int j = 0; j <= sectors; j++) {
                double sectorAngle = j * 2 * Math.PI / sectors;
                vertices.add(new Vector3D(xy * Math.cos(sectorAngle), xy * Math.sin(sectorAngle), z));
            }
        }
        List<Triangle> tris = new ArrayList<>();
        Color sphereColor = new Color(200, 200, 220);
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < sectors; j++) {
                int first = (i * (sectors + 1)) + j;
                int second = first + sectors + 1;
                tris.add(new Triangle(vertices.get(first), vertices.get(second), vertices.get(first + 1), sphereColor));
                tris.add(new Triangle(vertices.get(first + 1), vertices.get(second), vertices.get(second + 1), sphereColor));
            }
        }
        return new Mesh(tris);
    }
    
    public static Mesh loadFromObjFile(String filename, Color color) {
        List<Vector3D> vertices = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v": vertices.add(new Vector3D(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]))); break;
                    case "f":
                        int[] faceVertices = new int[parts.length - 1];
                        for (int i = 0; i < faceVertices.length; i++) faceVertices[i] = Integer.parseInt(parts[i + 1].split("/")[0]) - 1;
                        for (int i = 1; i < faceVertices.length - 1; i++) triangles.add(new Triangle(vertices.get(faceVertices[0]), vertices.get(faceVertices[i]), vertices.get(faceVertices[i + 1]), color));
                        break;
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to load or parse " + filename, "File Error", JOptionPane.ERROR_MESSAGE);
            return new Mesh(new ArrayList<>());
        }
        return new Mesh(triangles);
    }

    
    @Override public void keyPressed(KeyEvent e) {
        double rotationSpeed = 0.05;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:  rotationY -= rotationSpeed; break;
            case KeyEvent.VK_RIGHT: rotationY += rotationSpeed; break;
            case KeyEvent.VK_DOWN:    rotationX -= rotationSpeed; break;
            case KeyEvent.VK_UP:  rotationX += rotationSpeed; break;
        }
    }
    @Override public void keyTyped(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}

    
    static class Vector2D { double u, v; public Vector2D(double u, double v) { this.u = u; this.v = v; } }
    static class Vector3D { double x, y, z, w; public Vector3D(double x, double y, double z) { this.x = x; this.y = y; this.z = z; this.w = 1; } public void perspectiveDivide() { if (w != 0) { x /= w; y /= w; z /= w; } } public double length() { return Math.sqrt(x*x + y*y + z*z); } public Vector3D normalize() { double l = length(); return l == 0 ? new Vector3D(0,0,0) : new Vector3D(x/l, y/l, z/l); } public static Vector3D subtract(Vector3D v1, Vector3D v2) { return new Vector3D(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z); } public static double dotProduct(Vector3D v1, Vector3D v2) { return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z; } public static Vector3D crossProduct(Vector3D v1, Vector3D v2) { return new Vector3D(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z, v1.x * v2.y - v1.y * v2.x); } }
    static class Triangle { Vector3D p1, p2, p3; Vector2D t1, t2, t3; Color color; public Triangle(Vector3D p1, Vector3D p2, Vector3D p3, Vector2D t1, Vector2D t2, Vector2D t3, Color color) { this.p1 = p1; this.p2 = p2; this.p3 = p3; this.t1 = t1; this.t2 = t2; this.t3 = t3; this.color = color; } public Triangle(Vector3D p1, Vector3D p2, Vector3D p3, Color color) { this(p1, p2, p3, new Vector2D(0,0), new Vector2D(0,0), new Vector2D(0,0), color); } }
    static class Mesh { List<Triangle> tris; public Mesh(List<Triangle> tris) { this.tris = tris; } }
    static class Matrix4x4 {
        double[][] m = new double[4][4];
        public static Vector3D multiply(Matrix4x4 matrix, Vector3D vector) { Vector3D result = new Vector3D(0, 0, 0); result.x = vector.x * matrix.m[0][0] + vector.y * matrix.m[1][0] + vector.z * matrix.m[2][0] + vector.w * matrix.m[3][0]; result.y = vector.x * matrix.m[0][1] + vector.y * matrix.m[1][1] + vector.z * matrix.m[2][1] + vector.w * matrix.m[3][1]; result.z = vector.x * matrix.m[0][2] + vector.y * matrix.m[1][2] + vector.z * matrix.m[2][2] + vector.w * matrix.m[3][2]; result.w = vector.x * matrix.m[0][3] + vector.y * matrix.m[1][3] + vector.z * matrix.m[2][3] + vector.w * matrix.m[3][3]; return result; }
        public static Matrix4x4 multiply(Matrix4x4 a, Matrix4x4 b) { Matrix4x4 result = new Matrix4x4(); for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) result.m[r][c] = a.m[r][0] * b.m[0][c] + a.m[r][1] * b.m[1][c] + a.m[r][2] * b.m[2][c] + a.m[r][3] * b.m[3][c]; return result; }
        public static Matrix4x4 createRotationX(double angleRad) { Matrix4x4 rotX = new Matrix4x4(); rotX.m[0][0] = 1; rotX.m[1][1] = Math.cos(angleRad); rotX.m[1][2] = Math.sin(angleRad); rotX.m[2][1] = -Math.sin(angleRad); rotX.m[2][2] = Math.cos(angleRad); rotX.m[3][3] = 1; return rotX; }
        public static Matrix4x4 createRotationY(double angleRad) { Matrix4x4 rotY = new Matrix4x4(); rotY.m[0][0] = Math.cos(angleRad); rotY.m[0][2] = Math.sin(angleRad); rotY.m[2][0] = -Math.sin(angleRad); rotY.m[1][1] = 1; rotY.m[2][2] = Math.cos(angleRad); rotY.m[3][3] = 1; return rotY; }
        public static Matrix4x4 createRotationZ(double angleRad) { Matrix4x4 rotZ = new Matrix4x4(); rotZ.m[0][0] = Math.cos(angleRad); rotZ.m[0][1] = Math.sin(angleRad); rotZ.m[1][0] = -Math.sin(angleRad); rotZ.m[1][1] = Math.cos(angleRad); rotZ.m[2][2] = 1; rotZ.m[3][3] = 1; return rotZ; }
        public static Matrix4x4 createProjection(double fovDegrees, double aspectRatio, double near, double far) { Matrix4x4 proj = new Matrix4x4(); double fovRad = 1.0 / Math.tan(Math.toRadians(fovDegrees * 0.5)); proj.m[0][0] = aspectRatio * fovRad; proj.m[1][1] = fovRad; proj.m[2][2] = far / (far - near); proj.m[3][2] = (-far * near) / (far - near); proj.m[2][3] = 1.0; proj.m[3][3] = 0.0; return proj; }
    }
}

