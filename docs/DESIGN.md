# Jordan — Design

> Status: Draft v0.1 · Last updated: 2026-10-26
> Companion docs: [SPECIFICATION.md](SPECIFICATION.md) (what) · [TASKS.md](TASKS.md) (in what order)

## 1. Architecture overview

Jordan is a **kernel + modules** engine. `jordan-core` owns the world model (the Instance tree), the
game loop and the *contracts* for every subsystem. Each feature module implements one contract and
registers itself with the core at startup. Applications (`jordan-studio`, `jordan-player`) are thin:
they pick which modules to load and start the core.

```
                ┌──────────────────────┐      ┌──────────────────────┐
  apps          │    jordan-studio     │      │    jordan-player     │
                │  (editor, export)    │      │ (runs exported game) │
                └──────────┬───────────┘      └──────────┬───────────┘
                           │  loads modules & starts core │
                ┌──────────┴──────────────────────────────┴───────────┐
  feature       │ render │ physics │ audio │ scripting │ ui │ network │
  modules       │ assets │ serialization                              │
                └──────────┬──────────────────────────────────────────┘
                           │ implements contracts / registers services
                ┌──────────┴───────────┐
  platform      │   jordan-platform    │  window, input, GL context (LWJGL/GLFW)
                └──────────┬───────────┘
                ┌──────────┴───────────┐
  kernel        │     jordan-core      │  Instance tree, properties, signals,
                │                      │  services, module SPI, game loop, math
                └──────────────────────┘
```

**Dependency rule (NFR-6):** arrows point down only. A feature module may depend on `jordan-core`
and, if it needs a window or GPU context, on `jordan-platform`. Feature modules never depend on
each other; when one needs another (e.g. render needs textures), it asks the core for a *service
interface* defined in core (`AssetService`), never the concrete class. Gradle enforces this because
the implementations are not on each other's compile classpath.

## 2. Gradle layout

```
Jordan/
├── settings.gradle.kts          # includes all modules below
├── build.gradle.kts             # root: shared group/version only
├── gradle/libs.versions.toml    # version catalog (LWJGL, JOML, Jackson, jolt-jni, imgui-java, JUnit)
├── build-logic/                 # convention plugins
│   └── jordan.java-module.gradle.kts   # Java 25 toolchain, JUnit 6, module-info, test config
├── jordan-core/
├── jordan-platform/
├── jordan-render/
├── jordan-physics/
├── jordan-audio/
├── jordan-assets/
├── jordan-scripting/
├── jordan-serialization/
├── jordan-ui/
├── jordan-network/              # post-v1
├── jordan-studio/               # application
├── jordan-player/               # application
└── docs/
```

- Base package: `org.abdullahcxd.jordan.<module>` (e.g. `org.abdullahcxd.jordan.render`).
- Each module ships a `module-info.java` (JPMS). Internal packages are named `…internal` and are not exported.
- LWJGL natives are selected per OS via a Gradle property so `./gradlew :jordan-player:run` works on any desktop OS.

### Module responsibilities

| Module                 | Responsibility                                                                                           | Depends on        | Key libraries                            |
|------------------------|----------------------------------------------------------------------------------------------------------|-------------------|------------------------------------------|
| `jordan-core`          | Instance tree, properties, signals, class registry, services, module SPI, game loop, math types, logging | —                 | JOML (internal math), SLF4J API          |
| `jordan-platform`      | Window, GL context, input, `InputService`                                                                | core              | LWJGL (GLFW)                             |
| `jordan-render`        | Scene rendering, materials, lights, shadows, picking, debug draw                                         | core, platform    | LWJGL (OpenGL)                           |
| `jordan-physics`       | Rigid bodies, collisions, `Touched`, raycasts, character controller                                      | core              | jolt-jni                                 |
| `jordan-audio`         | `Sound` playback, 3D audio                                                                               | core              | LWJGL (OpenAL, stb_vorbis)               |
| `jordan-assets`        | File loading, caching, import, hot-reload, `AssetService`                                                | core              | LWJGL (stb_image), a glTF loader         |
| `jordan-scripting`     | JordanScript compiler + VM, engine bindings, sandbox, task scheduler                                     | core              | — (hand-written)                         |
| `jordan-serialization` | `.jplace` / `.jmodel` read/write, migrations                                                             | core              | Jackson                                  |
| `jordan-ui`            | `ScreenGui` & widgets, layout, input routing                                                             | core, platform    | (rendered via render's 2D batch service) |
| `jordan-studio`        | Editor app, panels, gizmos, undo, export                                                                 | all               | imgui-java                               |
| `jordan-player`        | Runtime app                                                                                              | all except studio | —                                        |

## 3. Core concepts

### 3.1 Instance tree

Modeled on Roblox's DataModel because it is the thing beginners already understand.

```java
public abstract class Instance {
    public String getName();            public void setName(String name);
    public Instance getParent();        public void setParent(Instance parent);   // null = detached
    public List<Instance> getChildren();
    public Instance findFirstChild(String name);
    public <T extends Instance> T findFirstChildOfClass(Class<T> type);
    public Instance clone();            // deep copy of this sub-tree
    public void destroy();              // detaches, locks parent, fires Destroying, disconnects signals

    public final Signal<PropertyChange> changed;
    public final Signal<Instance> childAdded, childRemoved;
    public final Signal<Void> destroying;
}
```

- Class hierarchy (initial): `Instance` → `DataModel`, `Folder`, `Model`, `Script`, `ModuleScript`, `Sound`, `Light` (`PointLight`, `SpotLight`), `Camera`, `BasePart` → (`Part`, `MeshPart`), service classes.
- Every class is registered in a `ClassRegistry` by name so the serializer, scripts (`Part()`) and Studio's Toolbox can create instances without knowing Java types.
- The tree is **owned by the main thread** (see §5). Mutating it from another thread throws.

### 3.2 Properties

Properties are declared once, statically, per class — this is what powers the Properties panel,
serialization, script types and undo without per-feature boilerplate.

```java
public class Part extends BasePart {
    public static final Property<Part, Vector3> SIZE =
        Property.of(Part.class, "Size", Vector3.class, Vector3.ONE)
                .category("Transform")
                .get(p -> p.size).set((p, v) -> p.size = v);
}
```

- `Property` holds name, type, default, getter/setter, category, flags (`READ_ONLY`, `NOT_SERIALIZED`, `HIDDEN`).
- Setting a property through `Property.set` fires `changed` and the per-property change signal.
- Supported value types are fixed: primitives, `String`, enums, the core math types, `Instance` references, `AssetId`. That keeps serialization and script marshalling closed and simple.

### 3.3 Signals

```java
Connection c = part.touched.connect(other -> ...);
part.touched.once(other -> ...);
c.disconnect();
```

- Single-threaded, synchronous dispatch on the main thread.
- A listener that throws is logged and does not stop other listeners (NFR-5).
- Destroying an Instance disconnects all its signals.

### 3.4 Math types

Immutable value types (`record`s) in `org.abdullahcxd.jordan.core.math`: `Vector2`, `Vector3`,
`CFrame`, `Color3`, `UDim`, `UDim2`, `Ray`, `Region3`. Immutable because they are shared freely
between Java, scripts and undo history. Hot paths in render/physics convert to JOML mutable types
internally.

### 3.5 Services and the module SPI

This is the "each module is used by the core" mechanism.

```java
// in jordan-core
public interface JordanModule {
    String id();                                   // "jordan.render"
    default Set<String> requires() { return Set.of(); }
    void init(EngineContext ctx);                  // register classes, services, systems
    default void start(EngineContext ctx) {}       // after all modules are initialised
    default void stop(EngineContext ctx) {}        // reverse order
}

public interface EngineContext {
    ClassRegistry classes();
    <T> void provide(Class<T> contract, T impl);   // e.g. provide(AssetService.class, impl)
    <T> T require(Class<T> contract);              // fails fast if no module provides it
    GameLoop loop();                               // add systems to update phases
    DataModel game();
}
```

- Modules are discovered with `java.util.ServiceLoader` (`provides JordanModule with …` in `module-info.java`).
- The core topologically sorts modules by `requires()`, fails at startup on a cycle or missing module.
- **Contracts live in core** (`RenderService`, `PhysicsService`, `AudioService`, `AssetService`, `ScriptService`, `InputService`, `WindowService`, `SerializationService`). Modules provide implementations. This lets `jordan-player` run headless tests with a null renderer, and lets someone swap the renderer without touching anything else.
- Roblox-style services visible in the tree (`Workspace`, `Lighting`, `RunService`, `Players`, `SoundService`…) are Instances under the DataModel; their behaviour is backed by the contract implementations above.

### 3.6 Game loop

Fixed-timestep simulation, variable-rate rendering:

```
while running:
    platform.pollEvents()                       → InputService signals
    accumulator += frameTime
    while accumulator >= 1/60:
        Stepped        (scripts: pre-physics)
        physics.step(1/60)                      → Touched signals, transforms written back to Parts
        Heartbeat      (scripts: post-physics)
        scripts.resumeParkedTasks()             → wait() / after()
        accumulator -= 1/60
    RenderStepped      (camera scripts)
    render.draw(alpha = accumulator / step)     → interpolated transforms
    window.swapBuffers()
```

Systems register into named phases (`INPUT`, `PRE_SIMULATION`, `SIMULATION`, `POST_SIMULATION`,
`PRE_RENDER`, `RENDER`) with an order value, so a new module can insert itself without editing the loop.

## 4. Subsystem design

### 4.1 Platform
GLFW via LWJGL. `WindowService` owns the window and GL context and exposes resize/focus events.
`InputService` translates GLFW callbacks into `InputObject`s (`KeyCode`, `UserInputType`, position,
delta) queued and dispatched in the `INPUT` phase so scripts always see input on the main thread.

### 4.2 Rendering
- OpenGL 4.1 core profile (D3: macOS cap). Renderer is a forward renderer with a single shadow-mapped sun (cascades later) and up to N clustered point/spot lights.
- The renderer does **not** walk the Instance tree each frame. It keeps a `RenderScene` of render proxies, updated from `changed`/`childAdded`/`childRemoved` signals on `Workspace` descendants. Same pattern in physics and audio.
- Built-in materials are PBR parameter presets + optional texture; `Neon` = emissive, `Glass` = transparent + reflective.
- Opaque pass → transparent pass (sorted back-to-front) → debug/gizmo pass → UI pass.
- Picking: ray from camera → CPU ray vs. bounding boxes, refined by physics raycast if available (no GPU readback needed).

### 4.3 Physics
- Jolt via jolt-jni (D5). Each non-detached `BasePart` gets a body: `Anchored` → static, otherwise dynamic.
- After each step, transforms are written back to Parts with a flag so that physics-originated writes do not re-enter physics as user edits.
- `Touched` is raised from Jolt contact-added callbacks, buffered, and fired on the main thread after the step.
- Character controller uses Jolt's `CharacterVirtual`.

### 4.4 Audio
OpenAL via LWJGL. `Sound` parented to a `BasePart` becomes a positional source updated each frame;
otherwise it is 2D. OGG decoded with stb_vorbis, WAV parsed directly. Listener follows the current camera.

### 4.5 Assets
- `AssetId` = `asset://<project-relative path>`. `AssetService.load(AssetId, Class<T>)` returns a handle with ref-count; the underlying file is loaded once.
- Decoding (images, glTF, audio) runs on a worker pool; GPU/AL uploads are queued back to the main thread.
- Studio watches `assets/` with `WatchService` and reloads changed files in place (FR-ASSET-4).

### 4.6 Scripting
Scripts are written in **JordanScript** (`.jds`), Jordan's own language: JavaScript-like syntax with
Java-style `<type> <name>` declarations and generics, plus Go-style `func`, multiple returns, `defer`,
`go` tasks and implicit interfaces (D2). The full language design is in [SCRIPTING.md](SCRIPTING.md).

- Pipeline, all in `jordan-scripting` with no third-party dependency: lexer → Pratt parser → type checker → bytecode compiler → VM. Hidden behind core's `ScriptService`.
- **Engine types** for the type checker come from a `TypeProvider` built from the `ClassRegistry` and `Property` metadata (§3.1–3.2), so Instance classes from any module are script types automatically, with no hand-written per-class glue. Property access compiles to direct `Property` handles.
- **Sandbox by construction:** a script can only reach what the VM exposes. There is no `eval`, reflection, file, process or network access (FR-SCR-3).
- Each task (a script's main body, a `go` call, a signal listener that waits) is a VM fiber. `wait()` and `Signal.wait()` park the fiber, and the scheduler resumes it in the `POST_SIMULATION` phase. An instruction counter per resume enforces the budget (FR-SCR-6).
- `game`, `workspace` and `script` globals are injected per script. `import … from "Path/To/Module"` resolves ModuleScripts in the Instance tree and caches exports per DataModel.
- An uncaught error ends only its task and is logged with script path, line, column and stack (FR-SCR-4).
- Studio compiles on every source edit for live diagnostics and autocomplete; exported games ship pre-compiled bytecode.

### 4.7 Serialization
`.jplace` = JSON (D6), stable key order and pretty-printed so diffs are reviewable in git:

```json
{
  "format": "jordan.place",
  "version": 1,
  "root": {
    "class": "DataModel",
    "children": [
      { "class": "Workspace", "id": "a1", "properties": { "Gravity": 196.2 },
        "children": [
          { "class": "Part", "id": "b7", "properties": {
              "Name": "Baseplate", "Anchored": true,
              "Size": [512, 20, 512], "CFrame": [0, -10, 0, 1,0,0, 0,1,0, 0,0,1] } } ] } ] }
}
```

- Only non-default property values are written.
- Instance-reference properties are written as the target's `id`; ids are stable across saves.
- `version` + a chain of `Migration` steps upgrades old files on load (FR-SER-3).
- Reflection-free: driven by the `Property` metadata, same as the script type provider.

### 4.8 In-game UI
Retained-mode Instances (`ScreenGui`, `Frame`, `TextLabel`…) laid out in a `PRE_RENDER` system, drawn by
a 2D batch service the renderer provides. Text via stb_truetype font atlas. Input is routed to UI first;
consumed events do not reach `InputService` game handlers.

## 5. Threading model

- **Main thread:** Instance tree, signals, scripts, game loop, GL and AL calls. Everything users touch is single-threaded — no locks in user-facing code.
- **Worker pool:** asset decoding, file I/O, export. Results come back via `loop().runOnMain(Runnable)`.
- **Physics:** Jolt uses its own job system internally during `step()`; the call itself is synchronous from the main thread's point of view.
- macOS requires GLFW on the first thread: apps launch with `-XstartOnFirstThread` on macOS (handled by the Gradle run task and the exported launcher).

## 6. Studio design

### 6.1 Edit mode vs Play mode
- **Edit mode:** the DataModel is loaded with only edit-safe modules active (render, assets, audio preview). Physics and scripts are not stepped.
- **Play:** Studio serializes the edit DataModel to memory, deserializes it into a fresh DataModel, and runs the full loop on that copy. **Stop** throws the copy away and returns to the untouched edit model — this guarantees FR-STU-11 for free and reuses the serializer.

### 6.2 UI
Dear ImGui (imgui-java) with docking (D4), rendered into the same GLFW window after the 3D viewport.
Default layout mirrors Roblox Studio: Explorer (top-right), Properties (bottom-right), Toolbox (left),
Output (bottom), viewport centre, toolbar with Select / Move / Rotate / Scale / Play / Stop / Export.

- **Explorer** and **Properties** are generic over the `ClassRegistry` and `Property` metadata — a new Instance class from any module appears with no Studio changes.
- **Script editor:** an ImGui text-editor widget (ImGuiColorTextEdit, bundled with imgui-java) with a JordanScript language definition; error markers and autocomplete come from the JordanScript type checker (SCRIPTING §12.1).

### 6.3 Undo / redo
Every edit goes through a `Command` (`SetProperty`, `Reparent`, `Insert`, `Delete`, `Composite`).
Gizmo drags and multi-select edits are merged into one `Composite`. Since all edits are property
writes or tree changes, a handful of command types cover everything.

### 6.4 Export
1. Save the current place and copy `assets/` used by it.
2. Build a classpath of `jordan-player` + the modules the project uses.
3. Run `jlink` to build a trimmed runtime, then `jpackage` to produce an app image (`.exe` folder / `.app` / Linux folder), with `-XstartOnFirstThread` on macOS.
4. Export targets the OS Studio is running on (cross-OS export is out of scope for v1).

## 7. Project layout on disk (user projects)

```
MyGame/
├── project.json        # name, version, start place, enabled modules, window settings
├── places/
│   └── Main.jplace
└── assets/
    ├── textures/
    ├── meshes/
    └── sounds/
```

Scripts are stored inside the place file as the `Source` property in v1. (Could later move to
separate `.jds` files for external editors.)

## 8. Error handling

- Every boundary where user content enters the engine (script resume, signal listener, asset decode, file load) catches exceptions, logs them with context, and continues (NFR-5).
- Loading a place with an unknown class or property logs a warning and keeps the data in an `UnknownInstance` so a later save does not lose it.
- Studio writes auto-save snapshots to `.jordan/autosave/` every 2 minutes (FR-STU-13).

## 9. Testing strategy

| Layer             | How                                                                                              |
|-------------------|--------------------------------------------------------------------------------------------------|
| core              | Plain JUnit 6 unit tests: tree operations, property change events, signal dispatch, module sort. |
| serialization     | Round-trip tests on golden `.jplace` files (FR-SER-4); migration tests per version.              |
| scripting         | Headless: lexer/parser/type-checker golden tests; run `.jds` snippets against a DataModel; sandbox tests.    |
| physics           | Headless simulation tests (a part falls and lands within N steps).                               |
| render / platform | Smoke test behind a `-Pgpu` flag; screenshot comparison later.                                   |
| studio            | Command/undo tests are headless; UI tested manually via a checklist.                             |
| CI                | GitHub Actions matrix: Windows, macOS, Linux run all headless tests.                             |

## 10. Decisions log

Defaults for open decisions are listed in [SPECIFICATION.md §9](SPECIFICATION.md#9-open-decisions).
Record changes here as they are made:

| Date       | Decision                       | Reason                                        |
|------------|--------------------------------|-----------------------------------------------|
| 2026-10-26 | Initial defaults D1–D6 adopted | Fastest path to a working, cross-platform v1. |
| 2026-10-26 | D2: custom language JordanScript replaces Lua | Owner wants JS-like syntax with Java-style types/generics and Go touches; see SCRIPTING.md. |
