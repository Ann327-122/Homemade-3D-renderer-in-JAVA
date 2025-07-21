# Homemade 3D Renderer in Java

This repository contains a 3D rendering engine and a first-person voxel demo, built entirely from scratch in Java. The goal was to understand and implement fundamental computer graphics concepts without relying on any external 3D libraries or frameworks beyond Java's built-in JDK features.

---

### Key Features of the Renderer
*   **Full 3D Graphics Pipeline:** Implements core steps including vertex transformation, projection, and clipping.
*   **Texture Mapping:** Supports applying 2D textures to 3D surfaces with perspective correction.
*   **Z-Buffering:** Utilizes a depth buffer for accurate occlusion handling, ensuring pixel-perfect rendering without visual seams.
*   **Flat Shading:** Simple lighting model applying a uniform color across each triangle based on its normal.
*   **Custom Math Library:** All vector and matrix operations are implemented from first principles.

### The Demos (Java Source Files)

This project includes two main runnable Java source files showcasing the renderer's capabilities:

1.  **`HomemadeRenderer.java` (Core Engine Demo)**
    *   **Purpose:** Demonstrates the raw rendering capabilities of the engine, showcasing textured cubes, spheres, and a loaded teapot model. You can switch between these by modifying the `renderVoxel`, `renderSphere`, and `renderObject` boolean flags at the top of the file.
    *   **Features:** Basic object rotation controls.
    *   **Note:** This demo maintains a unique camera perspective and control scheme that evolved during its development.

2.  **`RendererExample.java` (First-Person Voxel Grid Example)**
    *   **Purpose:** Illustrates how the renderer can be used to build an interactive 3D application, simulating a basic voxel-based environment.
    *   **Features:**
        *   Optimized world mesh generation (only visible faces rendered).
        *   First-person camera with mouse-look and keyboard (WASD) movement.
        *   Simple physics with gravity and ground collision.

---

### How to Run the Demos
To run these Java applications, you will need a Java Development Kit (JDK) installed on your system.

1.  **Download the project:** Download the entire repository as a `.zip` file from GitHub and extract it to a convenient location (e.g., `C:\MyProject\` on Windows, or `~/MyProject/` on Linux/macOS).
2.  **Open your terminal/command prompt:**
    *   **On Windows:** Open the **Command Prompt** or **PowerShell**.
    *   **On Linux/macOS:** Open **Terminal**.
3.  **Navigate to the project root:** Use the `cd` command to go to the main folder where you extracted the project.
    *   Example (Windows): `cd C:\MyProject\Homemade-3D-renderer-in-JAVA\`
    *   Example (Linux/macOS): `cd ~/MyProject/Homemade-3D-renderer-in-JAVA/`
4.  **Compile the Java files:**
    ```bash
    javac HomemadeRenderer.java RendererExample.java
    ```
5.  **Run the Demos:**

    *   **To run the core renderer demo:**
        ```bash
        java HomemadeRenderer
        ```
    *   **To run the first-person voxel game:**
        ```bash
        java RendererExample
        ```

### Required Assets
The project expects an `assets` folder in the root directory. This folder should contain:
*   `assets/textures/cube/Texture.png` (for the cube block texture)
*   `assets/Objects_(obj)/teapot.obj` (the 3D model for the teapot demo)
These assets must remain in their specified paths relative to the `.java` files for the demos to run correctly.

---

### Controls

#### For `HomemadeRenderer.java` (Object Viewer)
*   **Arrow Keys:** Rotate the displayed 3D model.

#### For `RendererExample.java` (First-Person Voxel Grid)
*   **WASD:** Move the camera (W=Forward, S=Backward, A=Strafe Left, D=Strafe Right).
*   **Mouse:** Look around.
*   **Click Window:** Lock the mouse cursor for continuous camera control.
*   **ESC:** Unlock the mouse cursor.

---

### License
This project is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License. You are free to share and adapt this work for non-commercial purposes, provided you give appropriate credit.

If you wish to use this engine for a commercial project, please contact me at: ann.dev.projects@gmail.com

---

### Credits
*   **Author:** Ann! :3
*   **Utah Teapot 3D Model:** Public domain, created by Martin Newell.

###Thanks!
Thank you for downloading! Feedback is appreciated greatly!
-Ann
:3
