# Jordan — Specification

> Status: Draft v0.1 · Last updated: 2026-10-26
> Companion docs: [DESIGN.md](DESIGN.md) (how) · [TASKS.md](TASKS.md) (in what order)

## 1. Vision

Jordan is a desktop game engine written in Java. It aims to be **as approachable as Roblox Studio** —
open the editor, drop parts into a world, attach a script, press Play — while producing **standalone
desktop games** (Windows, macOS, Linux) that the creator owns and ships wherever they want.

The engine is **multi-module**: a small `jordan-core` kernel defines the world model and the service
contracts, and every other module (rendering, physics, audio, scripting, …) plugs a concrete
implementation of one of those contracts into the core.

## 2. Target users

| User                            | Needs                                                                                                    |
|---------------------------------|----------------------------------------------------------------------------------------------------------|
| **Hobbyist / beginner creator** | Build a playable game without writing engine code. Drag-and-drop editor, simple scripting, instant Play. |
| **Indie developer**             | Ship a real desktop game, extend the engine, good performance, no royalties or platform lock-in.         |
| **Engine contributor**          | Clear module boundaries, a documented module API, tests.                                                 |

## 3. Goals

1. **Easy to use** — a newcomer can make a cube fall onto a baseplate and react to a key press within 10 minutes of first launch.
2. **Powerful** — 3D rendering with PBR-style materials, rigid-body physics, spatial audio, scripting, in-game UI.
3. **Modular** — each subsystem is its own Gradle module behind a core-defined contract; modules can be replaced or omitted.
4. **Desktop-first** — runs and exports to Windows, macOS and Linux.
5. **Own your game** — export produces a self-contained executable; no account, server, or store required.

## 4. Non-goals (v1)

- Mobile, console, or web targets.
- A hosted game platform, marketplace, or user accounts.
- AAA rendering features (ray tracing, virtual geometry, global illumination).
- Visual (node-based) scripting.
- Built-in multiplayer (planned for after v1, see §7.10).

## 5. Glossary

| Term | Meaning |
|------|---------|
| **Instance** | Any object in the world tree (a Part, a Light, a Script, a Folder…). Has a class, a name, a parent, children, and properties. |
| **DataModel** | The root Instance of a running game. Owns all services. |
| **Service** | A singleton Instance directly under the DataModel that provides a subsystem (`Workspace`, `Lighting`, `InputService`, `RunService`…). |
| **Place** | A saved DataModel on disk (`.jplace`). The unit you open in the editor. |
| **Part** | A basic 3D primitive (block, sphere, cylinder, wedge) with physics and a material. |
| **Script** | An Instance holding JordanScript source (see [SCRIPTING.md](SCRIPTING.md)) that runs when the game runs. |
| **Signal** | An event an Instance exposes (`Touched`, `Changed`, `ChildAdded`…) that scripts and Java code can connect to. |
| **Module** | A Gradle subproject that implements a core contract (e.g. `jordan-render`). |
| **Studio** | The Jordan editor application. |
| **Player** | The Jordan runtime that runs a place without the editor (used by exported games). |

## 6. Functional requirements

Requirement IDs are referenced from [TASKS.md](TASKS.md). Priority: **M** = must for v1, **S** = should, **C** = could.

### 6.1 Core — `jordan-core`

| ID        | Requirement                                                                                                                     | Pri |
|-----------|---------------------------------------------------------------------------------------------------------------------------------|-----|
| FR-CORE-1 | Provide the Instance tree: create, name, parent/re-parent, find children, destroy, clone.                                       | M   |
| FR-CORE-2 | Typed, reflectable properties on every Instance class (name, type, default, read-only flag, category for the Properties panel). | M   |
| FR-CORE-3 | Signals: `Changed`, `ChildAdded`, `ChildRemoved`, `Destroying`, plus class-specific signals; connect/disconnect/once.           | M   |
| FR-CORE-4 | Services: DataModel exposes `getService(Class)`; services are registered by modules.                                            | M   |
| FR-CORE-5 | Module system: discover modules at startup, resolve their dependencies, run their lifecycle (init → start → stop).              | M   |
| FR-CORE-6 | Game loop with fixed-timestep simulation and variable-rate rendering; exposes `Stepped`, `Heartbeat`, `RenderStepped` signals.  | M   |
| FR-CORE-7 | Math value types: `Vector3`, `Vector2`, `CFrame` (position + rotation), `Color3`, `UDim2`, `Ray`, `Region3`.                    | M   |
| FR-CORE-8 | Logging that is visible both in the console and in Studio's Output panel.                                                       | M   |
| FR-CORE-9 | Class registry so new Instance classes (including from modules) are discoverable by name.                                       | M   |

### 6.2 Platform — `jordan-platform`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PLAT-1 | Create and manage a window (title, size, fullscreen, vsync, resize events). | M |
| FR-PLAT-2 | Keyboard, mouse (position, delta, buttons, wheel, cursor lock) input. | M |
| FR-PLAT-3 | Gamepad input. | S |
| FR-PLAT-4 | `InputService` exposing `InputBegan`, `InputEnded`, `InputChanged`, and polling (`isKeyDown`). | M |
| FR-PLAT-5 | Action mapping (bind named actions to keys/buttons). | S |

### 6.3 Rendering — `jordan-render`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-REND-1 | Render all visible Parts and Meshes in `Workspace` from the current `Camera`. | M |
| FR-REND-2 | Materials: color, roughness, metalness, texture; a set of built-in materials (Plastic, Wood, Metal, Grass, Neon, Glass…). | M |
| FR-REND-3 | Lighting: one directional sun, ambient, point and spot lights; settings in the `Lighting` service. | M |
| FR-REND-4 | Real-time shadows from the sun. | M |
| FR-REND-5 | Skybox. | S |
| FR-REND-6 | Transparency. | M |
| FR-REND-7 | Debug drawing (lines, boxes, gizmos) used by Studio. | M |
| FR-REND-8 | Object picking (screen point → Instance) used by Studio. | M |
| FR-REND-9 | Post-processing: bloom, tone mapping, color correction. | C |

### 6.4 Physics — `jordan-physics`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PHYS-1 | Rigid-body simulation for Parts (`Anchored`, `CanCollide`, `Mass`/`Density`, `Friction`, `Elasticity`). | M |
| FR-PHYS-2 | `Touched` / `TouchEnded` signals. | M |
| FR-PHYS-3 | Raycasts and overlap queries from scripts. | M |
| FR-PHYS-4 | Constraints: Weld, Hinge, Spring, Rope. | S |
| FR-PHYS-5 | Character controller (walk, jump, slopes, steps) for the default player character. | M |
| FR-PHYS-6 | Collision groups. | S |

### 6.5 Audio — `jordan-audio`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-AUD-1 | `Sound` Instance: play, stop, pause, loop, volume, pitch. | M |
| FR-AUD-2 | 3D positional sound when parented to a Part. | M |
| FR-AUD-3 | Formats: OGG and WAV. | M |
| FR-AUD-4 | Sound groups / mixer volumes. | C |

### 6.6 Assets — `jordan-assets`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-ASSET-1 | Load textures (PNG, JPG), meshes (glTF 2.0, OBJ), and sounds from the project's `assets/` folder. | M |
| FR-ASSET-2 | Asset references by project-relative id (`asset://textures/grass.png`). | M |
| FR-ASSET-3 | Caching and reference counting; load each file once. | M |
| FR-ASSET-4 | Hot-reload assets changed on disk while Studio is open. | S |
| FR-ASSET-5 | Asynchronous loading off the main thread. | S |

### 6.7 Scripting — `jordan-scripting`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-SCR-1 | Run JordanScript (`.jds`) scripts: `Script` = game logic, `LocalScript` reserved for client once networking exists, `ModuleScript` shared via `import`/`export`. | M |
| FR-SCR-2 | Language: JavaScript-like syntax with Java-style `<type> <name>` declarations, generics (`List<T>`, `Map<K, V>`, user generics), null-safe `T?` types, and Go-style `func`, multiple returns, `defer`, `go` and implicit interfaces. | M |
| FR-SCR-2a | Static type checking before Play; errors and autocomplete in Studio. Engine Instance classes, properties, signals, services and math types are typed automatically from core metadata (`Part p = Part(); p.position = Vector3(0, 10, 0)`). | M |
| FR-SCR-3 | Sandbox: no file system, process, or reflection access from scripts. | M |
| FR-SCR-4 | Errors show script name, line, and stack in Output; one failing script never crashes the engine. | M |
| FR-SCR-5 | Cooperative tasks: `wait()`, `go fn()`, `after(t, fn)`, `signal.wait()`. | M |
| FR-SCR-6 | Per-script execution time budget to stop runaway loops. | S |
| FR-SCR-7 | Java "plugins" — users can add their own Java module to a project. | C |

### 6.8 Serialization — `jordan-serialization`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-SER-1 | Save and load a whole DataModel to a place file (`.jplace`), human-readable (JSON) for diffing in git. | M |
| FR-SER-2 | Save and load a sub-tree as a model file (`.jmodel`) for reuse. | M |
| FR-SER-3 | Format versioning with forward migration of old files. | M |
| FR-SER-4 | Round-trip guarantee: load → save produces identical output. | M |

### 6.9 In-game UI — `jordan-ui`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-UI-1 | `ScreenGui` with `Frame`, `TextLabel`, `TextButton`, `ImageLabel`, `TextBox`. | S |
| FR-UI-2 | Layout with `UDim2` (scale + offset), anchor point, z-index. | S |
| FR-UI-3 | Mouse and keyboard interaction (click, hover, focus). | S |

### 6.10 Networking — `jordan-network` (post-v1)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-NET-1 | Client/server model with automatic replication of the Instance tree. | C |
| FR-NET-2 | `RemoteEvent` / `RemoteFunction`. | C |

### 6.11 Studio (editor) — `jordan-studio`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-STU-1 | Project management: create, open, recent projects. A project = folder with `project.json`, `places/`, `assets/`. | M |
| FR-STU-2 | 3D viewport with fly camera (WASD + right mouse), focus on selection (F). | M |
| FR-STU-3 | **Explorer** panel: the Instance tree; select, rename, re-parent by drag-and-drop, insert, delete. | M |
| FR-STU-4 | **Properties** panel: edit the selected Instance's properties, grouped by category. | M |
| FR-STU-5 | Move / Rotate / Scale gizmos with grid snapping. | M |
| FR-STU-6 | **Toolbox**: insert Parts, lights, sounds, and project assets. | M |
| FR-STU-7 | **Output** panel: logs and script errors, clickable to open the script. | M |
| FR-STU-8 | Script editor with JordanScript syntax highlighting, line numbers, live type errors and autocomplete. | M |
| FR-STU-9 | Undo / redo for every edit. | M |
| FR-STU-10 | Copy / paste / duplicate Instances. | M |
| FR-STU-11 | **Play / Stop**: run the place inside Studio; Stop restores the pre-Play state exactly. | M |
| FR-STU-12 | **Export**: build a standalone game for the current OS. | M |
| FR-STU-13 | Auto-save and crash recovery. | S |
| FR-STU-14 | Studio extensions written in JordanScript or Java. | C |

### 6.12 Player (runtime) — `jordan-player`

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PLY-1 | Launch a place without the editor. | M |
| FR-PLY-2 | Default player character + camera controller, replaceable by scripts. | M |
| FR-PLY-3 | Basic settings (resolution, fullscreen, volume). | S |

## 7. Non-functional requirements

| ID    | Requirement                                                                                                                                                                     |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-1 | **Performance:** 60 FPS with 5,000 Parts and 50 active scripts on a mid-range GPU (GTX 1060 / RX 580 class) at 1080p.                                                           |
| NFR-2 | **Startup:** Studio opens an empty project in < 5 s; an exported game reaches its first frame in < 3 s.                                                                         |
| NFR-3 | **Platforms:** Windows 10+, macOS 12+ (Apple Silicon and Intel), Ubuntu 22.04+.                                                                                                 |
| NFR-4 | **Java:** Java 25 (LTS). Exported games bundle their own runtime; end users never install Java.                                                                                 |
| NFR-5 | **Stability:** a script error or asset load failure never crashes Studio or the Player.                                                                                         |
| NFR-6 | **Modularity:** a module only depends on `jordan-core` (and `jordan-platform` where it needs a window or GPU context). No module depends on another feature module's internals. |
| NFR-7 | **Testability:** `jordan-core`, `jordan-serialization` and `jordan-scripting` run their tests headless with no GPU or window.                                                   |
| NFR-8 | **Export size:** a minimal exported game is under 150 MB.                                                                                                                       |
| NFR-9 | **Documentation:** every public API in `jordan-core` and every script-exposed type has docs.                                                                                      |

## 8. Constraints & assumptions

- Build: Gradle (Kotlin DSL), wrapper 9.6 as already set up in the repo. Group id `org.abdullahcxd`.
- Native access (window, OpenGL, OpenAL) through LWJGL 3.
- License: MIT. Bundled third-party libraries must be permissively licensed (MIT, BSD, Apache-2.0, zlib); no GPL.

## 9. Open decisions

These are chosen as defaults in [DESIGN.md](DESIGN.md); change them early if you disagree.

| # | Decision | Default chosen | Alternatives |
|---|----------|----------------|--------------|
| D1 | 2D, 3D, or both | 3D (2D via UI and an orthographic camera) | Dedicated 2D pipeline |
| D2 | Scripting language | **Decided:** JordanScript, a custom typed language with its own compiler + VM ([SCRIPTING.md](SCRIPTING.md)) | Lua (LuaJ), JavaScript (GraalJS) |
| D3 | Graphics API | OpenGL 4.1 core (highest version macOS supports) | Vulkan (+ MoltenVK on macOS) |
| D4 | Editor UI toolkit | Dear ImGui (imgui-java) with docking | JavaFX, Swing |
| D5 | Physics library | Jolt Physics (jolt-jni) | Bullet (libbulletjme), custom |
| D6 | Place file format | JSON | Binary, XML |
| D7 | License | **Decided:** MIT ([LICENSE](../LICENSE)) | Apache-2.0, GPL |
