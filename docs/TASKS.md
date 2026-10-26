# Jordan — Tasks

> Status: Draft v0.1 · Last updated: 2026-10-26
> Companion docs: [SPECIFICATION.md](SPECIFICATION.md) (what) · [DESIGN.md](DESIGN.md) (how)

Work is split into milestones. Each milestone ends with something you can run. Tasks reference
requirement IDs from the specification. Tick the box when the task's **Done when** is true.

Legend: `T-<milestone>.<n>` · **Req** = requirements covered · **Dep** = tasks that must be done first.

---

## M0 — Project setup

Goal: an empty multi-module build that compiles and tests on all three OSes.

- [x] **T-0.1 Version catalog** — add `gradle/libs.versions.toml` with LWJGL, JOML, Jackson, jolt-jni, imgui-java, SLF4J, JUnit.
  Done when: root build resolves all dependencies.
- [x] **T-0.2 Convention plugin** — `build-logic/` with `jordan.java-module` (Java 25 toolchain, JUnit platform, UTF-8, `-Xlint:all`). Move current root `build.gradle.kts` config into it.
  Done when: a module applying only `id("jordan.java-module")` builds and runs tests.
- [x] **T-0.3 Module skeletons** — create every module from DESIGN §2 (`jordan-network` deferred until post-v1) with a `module-info.java` and an empty package; include them in `settings.gradle.kts`.
  Dep: T-0.2. Done when: `./gradlew build` passes.
- [ ] **T-0.4 LWJGL natives per OS** — pick native classifiers from `os.name`/`os.arch`.
  Dep: T-0.1. Done when: the same build works on Windows, macOS (arm64/x64), Linux.
  Status: implemented in `build-logic/src/main/kotlin/Natives.kt` (LWJGL, jolt-jni, imgui-java); verified on Windows x64, other OSes verified by CI (T-0.5).
- [ ] **T-0.5 CI** — GitHub Actions matrix (windows/macos/ubuntu) running `./gradlew build`.
  Dep: T-0.3. Status: `.github/workflows/build.yml` written; runs once the repo is pushed to GitHub.
- [x] **T-0.6 Git & repo hygiene** — `git init`, `.gitignore` for `.gradle/`, `build/`, `.idea/`; README pointing at `docs/`; MIT license (Spec D7).

## M1 — Core kernel (headless)

Goal: build, modify and observe an Instance tree from Java tests, with modules loading.

- [ ] **T-1.1 Math types** — `Vector2`, `Vector3`, `CFrame`, `Color3`, `UDim`, `UDim2`, `Ray`, `Region3` as records with operations and tests. Req: FR-CORE-7.
- [ ] **T-1.2 Signals** — `Signal<T>`, `Connection`, `connect`/`once`/`disconnect`; listener exceptions are logged, not propagated. Req: FR-CORE-3.
- [ ] **T-1.3 Property system** — `Property<O,V>` with metadata, flags, categories, per-property change signal. Req: FR-CORE-2. Dep: T-1.2.
- [ ] **T-1.4 Instance & tree** — `Instance` with parent/children, find, clone, destroy, `changed`/`childAdded`/`childRemoved`/`destroying`; main-thread guard. Req: FR-CORE-1. Dep: T-1.3.
- [ ] **T-1.5 Class registry** — register/create by name; base classes `Folder`, `Model`, `DataModel`. Req: FR-CORE-9. Dep: T-1.4.
- [ ] **T-1.6 Service contracts** — interfaces `WindowService`, `InputService`, `RenderService`, `PhysicsService`, `AudioService`, `AssetService`, `ScriptService`, `SerializationService`; `DataModel.getService`. Req: FR-CORE-4.
- [ ] **T-1.7 Module SPI** — `JordanModule`, `EngineContext`, `ServiceLoader` discovery, topological sort, lifecycle; fail fast on cycles/missing deps. Req: FR-CORE-5. Dep: T-1.6.
- [ ] **T-1.8 Game loop** — fixed-step loop with phases and `Stepped`/`Heartbeat`/`RenderStepped`; `runOnMain`. Req: FR-CORE-6. Dep: T-1.7.
- [ ] **T-1.9 Logging** — SLF4J facade + in-memory sink Studio's Output panel can subscribe to. Req: FR-CORE-8.
- [ ] **T-1.10 World classes** — `BasePart`, `Part` (Shape, Size, CFrame, Color, Material, Transparency, Anchored, CanCollide), `Workspace`, `Lighting`, `Camera`, `RunService`.
  Dep: T-1.5. **Milestone check:** a test builds a Workspace with Parts, edits them, and asserts on fired signals.

## M2 — Serialization

Goal: places survive save/load.

- [ ] **T-2.1 Place writer** — metadata-driven JSON writer, non-default props only, stable ids & key order. Req: FR-SER-1. Dep: T-1.10.
- [ ] **T-2.2 Place reader** — class registry lookups, instance references, `UnknownInstance` fallback. Req: FR-SER-1.
- [ ] **T-2.3 Model files** — `.jmodel` for sub-trees. Req: FR-SER-2.
- [ ] **T-2.4 Versioning & migrations** — `Migration` chain. Req: FR-SER-3.
- [ ] **T-2.5 Golden round-trip tests.** Req: FR-SER-4.

## M3 — Window, input, first pixels

Goal: a window shows a lit grey baseplate and a red cube; the camera flies.

- [ ] **T-3.1 Window module** — GLFW window, GL 4.1 core context, vsync, resize; `-XstartOnFirstThread` on macOS. Req: FR-PLAT-1.
- [ ] **T-3.2 Input** — keyboard/mouse into `InputService` (`InputBegan/Ended/Changed`, `isKeyDown`, cursor lock). Req: FR-PLAT-2, FR-PLAT-4. Dep: T-3.1.
- [ ] **T-3.3 Render scene proxies** — mirror `Workspace` descendants via signals. Req: FR-REND-1. Dep: T-1.10.
- [ ] **T-3.4 Mesh pipeline** — shader loading, built-in meshes for block/sphere/cylinder/wedge, instanced draw. Req: FR-REND-1.
- [ ] **T-3.5 Basic lighting** — sun + ambient from `Lighting`, Lambert/Blinn first. Req: FR-REND-3.
- [ ] **T-3.6 Camera** — `Camera` Instance → view/projection; debug fly controller.
- [ ] **T-3.7 Player app v0** — `jordan-player` loads a `.jplace` from args and runs the loop. Req: FR-PLY-1. Dep: T-2.2, T-3.5.
  **Milestone check:** `./gradlew :jordan-player:run --args=samples/Baseplate.jplace` shows the scene on all 3 OSes.

## M4 — Physics

Goal: unanchored parts fall, collide, and fire `Touched`.

- [ ] **T-4.1 Jolt integration** — init/shutdown, bodies for parts, step in `SIMULATION` phase, write-back with re-entrancy flag. Req: FR-PHYS-1.
- [ ] **T-4.2 Property sync** — Anchored, CanCollide, Size, Density, Friction, Elasticity changes update bodies. Req: FR-PHYS-1.
- [ ] **T-4.3 Touched / TouchEnded.** Req: FR-PHYS-2.
- [ ] **T-4.4 Queries** — `Workspace:Raycast`, overlap queries. Req: FR-PHYS-3.
- [ ] **T-4.5 Render interpolation** — interpolate transforms with loop alpha.
- [ ] **T-4.6 Headless physics tests** (part lands on baseplate within N steps).

## M5 — Scripting

Goal: a JordanScript script makes a part change color when touched. Language design: [SCRIPTING.md](SCRIPTING.md).

The language front-end (T-5.1–T-5.4) does not need the engine, so it can start in parallel with M1–M4.

*Language front-end*
- [ ] **T-5.1 Lexer** — all tokens from SCRIPTING §2, template strings, semicolon insertion, line/column positions. Golden-file tests.
- [ ] **T-5.2 Parser & AST** — recursive descent + Pratt expressions for the grammar in SCRIPTING §13; error recovery reports several errors per file. Req: FR-SCR-2.
- [ ] **T-5.3 Type checker** — scopes, `<type> <name>` declarations, `var` inference, `int`/`float` rules, `T?` null safety with narrowing, `as`/`as?`/`is`, multiple returns. Req: FR-SCR-2, FR-SCR-2a.
- [ ] **T-5.4 Generics, classes, interfaces, enums** — generic funcs/classes with `extends` bounds and inference, single inheritance, implicit (structural) interfaces, `_private` members. Req: FR-SCR-2.

*Back-end & runtime*
- [ ] **T-5.5 Bytecode & compiler** — instruction set, constant pool, closures, `defer`, try/catch/finally.
- [ ] **T-5.6 VM & fibers** — interpreter loop, fiber per task, `wait()` parking, `go`, `after`, `Task.cancel`. Req: FR-SCR-5.
- [ ] **T-5.7 Standard library** — globals, `math`, string/List/Map/Set methods, `json` (SCRIPTING §11).
- [ ] **T-5.8 Errors & budget** — runtime error types, stack traces with script/line/column to the log, per-resume instruction budget → `TimeoutError`. Req: FR-SCR-4, FR-SCR-6.
- [ ] **T-5.9 Sandbox tests** — prove there is no way to reach host classes, files or processes. Req: FR-SCR-3.

*Engine integration*
- [ ] **T-5.10 TypeProvider** — engine types from `ClassRegistry` + `Property` metadata, camelCase member names, engine-generic methods (`findFirstChild<T>`, `getService<T>`) get the runtime class. Dep: T-1.5, T-5.3. Req: FR-SCR-2a.
- [ ] **T-5.11 Native bindings** — property get/set via `Property` handles, methods as `NativeFunction`s, `Signal<T>` (`connect`/`once`/`wait`), math value types with operators.
- [ ] **T-5.12 Script lifecycle** — `Script`/`ModuleScript` Instances, compile + run on Play, `import`/`export` resolved in the Instance tree with per-DataModel cache, cancel tasks on destroy/Stop; scheduler in `POST_SIMULATION`. Req: FR-SCR-1.
- [ ] **T-5.13 Bytecode cache** — cache by source hash; pre-compiled bytecode for export.
  **Milestone check:** sample place with a touch-to-recolor `.jds` script runs in the Player, and a type error in it is reported before it runs.

## M6 — Studio MVP

Goal: build the M5 sample entirely in Studio and press Play.

- [ ] **T-6.1 ImGui integration** — imgui-java with docking in the GLFW window, viewport renders to a framebuffer shown in a dock panel. Req: FR-STU-2.
- [ ] **T-6.2 Project manager** — new/open/recent; project folder layout per DESIGN §7. Req: FR-STU-1.
- [ ] **T-6.3 Editor camera** — WASD + RMB fly, F to focus. Req: FR-STU-2.
- [ ] **T-6.4 Selection & picking** — click to select, outline highlight. Req: FR-REND-8. Dep: T-6.1.
- [ ] **T-6.5 Command system & undo/redo.** Req: FR-STU-9.
- [ ] **T-6.6 Explorer panel** — tree, rename, drag re-parent, insert, delete. Req: FR-STU-3. Dep: T-6.5.
- [ ] **T-6.7 Properties panel** — metadata-driven editors per value type. Req: FR-STU-4. Dep: T-6.5.
- [ ] **T-6.8 Gizmos** — move/rotate/scale with snapping, debug-draw. Req: FR-STU-5, FR-REND-7.
- [ ] **T-6.9 Toolbox** — insert parts, lights, sounds, scripts. Req: FR-STU-6.
- [ ] **T-6.10 Output panel** — log sink view, click-to-open script. Req: FR-STU-7.
- [ ] **T-6.11 Script editor** — JordanScript highlighting, live type errors, autocomplete and hover docs from the type checker. Req: FR-STU-8.
- [ ] **T-6.12 Copy/paste/duplicate** via `.jmodel` in-memory. Req: FR-STU-10.
- [ ] **T-6.13 Play / Stop** — serialize → fresh DataModel → run; Stop discards. Req: FR-STU-11.
- [ ] **T-6.14 Save / Save As / dirty tracking.**

## M7 — Look & feel

- [ ] **T-7.1 PBR materials & built-in material set.** Req: FR-REND-2.
- [ ] **T-7.2 Sun shadows** (shadow map, later cascades). Req: FR-REND-4.
- [ ] **T-7.3 Point & spot lights.** Req: FR-REND-3.
- [ ] **T-7.4 Transparency pass.** Req: FR-REND-6.
- [ ] **T-7.5 Skybox.** Req: FR-REND-5.
- [ ] **T-7.6 Performance pass** — profile against NFR-1 (5,000 parts, 50 scripts, 60 FPS).

## M8 — Assets & audio

- [ ] **T-8.1 AssetService** — `AssetId`, cache, ref-count, async decode. Req: FR-ASSET-1–3, FR-ASSET-5.
- [ ] **T-8.2 Textures** (stb_image) + `Texture` property on parts/decals.
- [ ] **T-8.3 Meshes** — glTF 2.0 / OBJ → `MeshPart`.
- [ ] **T-8.4 Audio module** — OpenAL device, `Sound` Instance, OGG/WAV, 3D positioning. Req: FR-AUD-1–3.
- [ ] **T-8.5 Hot-reload** in Studio. Req: FR-ASSET-4.
- [ ] **T-8.6 Toolbox asset browser** for project assets.

## M9 — Gameplay basics

- [ ] **T-9.1 Character controller** (Jolt `CharacterVirtual`). Req: FR-PHYS-5.
- [ ] **T-9.2 Default character + follow camera**, replaceable by scripts. Req: FR-PLY-2.
- [ ] **T-9.3 Constraints** — Weld, Hinge, Spring, Rope. Req: FR-PHYS-4.
- [ ] **T-9.4 Collision groups.** Req: FR-PHYS-6.
- [ ] **T-9.5 Gamepad & action mapping.** Req: FR-PLAT-3, FR-PLAT-5.

## M10 — In-game UI

- [ ] **T-10.1 2D batch renderer + font atlas** (stb_truetype).
- [ ] **T-10.2 `ScreenGui`, `Frame`, `TextLabel`, `ImageLabel`.** Req: FR-UI-1.
- [ ] **T-10.3 `UDim2` layout, anchor, z-index.** Req: FR-UI-2.
- [ ] **T-10.4 `TextButton`, `TextBox`, input routing.** Req: FR-UI-3.

## M11 — Export & v1 release

- [ ] **T-11.1 Export pipeline** — jlink + jpackage per DESIGN §6.4. Req: FR-STU-12. Check NFR-8 (<150 MB).
- [ ] **T-11.2 Player settings menu** — resolution, fullscreen, volume. Req: FR-PLY-3.
- [ ] **T-11.3 Auto-save & crash recovery.** Req: FR-STU-13.
- [ ] **T-11.4 Startup time pass** against NFR-2.
- [ ] **T-11.5 Docs** — JordanScript language guide + API reference generated from the TypeProvider; getting-started tutorial (the 10-minute goal from Spec §3). Req: NFR-9.
- [ ] **T-11.6 Sample games** — obby, physics sandbox.
- [ ] **T-11.7 v1.0 release** — tagged builds of Studio for all 3 OSes.

## Post-v1 backlog

- Networking: client/server replication, `RemoteEvent`/`RemoteFunction` (FR-NET-1, FR-NET-2).
- Post-processing: bloom, tone mapping, color correction (FR-REND-9).
- Sound groups / mixer (FR-AUD-4).
- Java plugins in projects (FR-SCR-7) and Studio extensions (FR-STU-14).
- Terrain, particles, animation/skinned meshes, Vulkan backend, scripts as external `.jds` files, JordanScript language server (LSP) for VS Code / IntelliJ, JVM-bytecode backend for hot scripts.
