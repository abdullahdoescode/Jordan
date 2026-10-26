# JordanScript — Language Design

> Status: Draft v0.1 · Last updated: 2026-10-26
> Companion docs: [SPECIFICATION.md](SPECIFICATION.md) §6.7 · [DESIGN.md](DESIGN.md) §4.6 · [TASKS.md](TASKS.md) M5

JordanScript (file extension `.jds`) is Jordan's own scripting language. It is built into the
engine (`jordan-scripting`) and is what creators use to write game logic.

**In one line:** JavaScript's syntax and feel, Java's `<type> <name>` declarations and generics,
and a few Go ideas (`func`, multiple return values, `defer`, `go` for concurrent tasks, implicit
interfaces).

```jds
import { Players } from "game"

const int COIN_VALUE = 10
Map<Player, int> coins = {}

Part coin = script.parent as Part
coin.material = Material.Neon

coin.touched.connect((BasePart other) => {
    Player? player = Players.getPlayerFromCharacter(other.parent)
    if (player == null) return

    coins[player] = (coins.get(player) ?? 0) + COIN_VALUE
    coin.destroy()
    print(`${player.name} has ${coins[player]} coins`)
})
```

## 1. Goals

1. **Familiar** — anyone who has seen JavaScript, Java, C# or Go can read it on day one.
2. **Typed** — declarations carry types, so Studio can show errors and autocomplete *before* you press Play.
3. **Safe** — scripts can only touch what the engine exposes. No files, processes, or host reflection (FR-SCR-3).
4. **Game-friendly** — waiting, events and concurrent tasks are built into the language, not bolted on.
5. **Fast enough** — compiled to bytecode once; 50 active scripts must fit in the frame budget (NFR-1).

Non-goals: full JavaScript compatibility, npm packages, operator overloading for user types,
inheritance-heavy OOP (classes exist, but implicit interfaces are preferred).

## 2. Lexical basics

- Source is UTF-8. Identifiers: letters, digits, `_`; cannot start with a digit.
- Comments: `// line`, `/* block */`, `/** doc */` (doc comments feed Studio tooltips and the API docs).
- **Semicolons are optional** (Go-style automatic insertion at line ends after a token that can end a statement).
- Braces are required for blocks; parentheses around `if`/`while`/`for` conditions are required (JS/Java style).
- Literals: `42`, `0xFF`, `1_000_000`, `3.14`, `1e-3`, `true`, `false`, `null`, `"text"`, `'text'`,
  template strings `` `Hello ${name}` ``, list `[1, 2, 3]`, map `{ "gold": 5, "gems": 1 }`.

**Keywords:** `import export from as is const var func return if else for in while break continue
switch case default class extends interface enum constructor this super new null true false
try catch finally throw defer go void`

(`wait`, `after` and `typeof` are built-in functions, not keywords.)

## 3. Types

### 3.1 Built-in types

| Type | Meaning |
|------|---------|
| `int` | 64-bit signed integer |
| `float` | 64-bit floating point |
| `bool` | `true` / `false` |
| `string` | immutable UTF-8 text |
| `void` | no value (return type only) |
| `any` | opt-out of static checking; checked at runtime instead |
| `List<T>` | growable ordered list |
| `Map<K, V>` | hash map; `K` must be `int`, `string`, `bool`, an enum, or an Instance |
| `Set<T>` | hash set |
| `func(A, B) R` | function type, e.g. `func(int, int) int`, `func(BasePart) void` |
| `T?` | nullable `T` (see §3.3) |

Engine types (`Instance`, `Part`, `Vector3`, `CFrame`, `Color3`, `Signal<T>`, enums like `Material`, …)
are generated from core's class registry and `Property` metadata (DESIGN §3.2), so every Instance
class from any module is automatically a JordanScript type.

`int` and `float` do not mix implicitly except that an `int` widens to `float`. `float → int` needs
`int(x)` (truncates) or `math.round(x)`.

### 3.2 Declarations — `<type> <name>`

```jds
int lives = 3
float speed = 16.5
string greeting = "hi"
List<Part> parts = []
Map<string, int> scores = {}
Vector3 spawn = Vector3(0, 10, 0)

var target = workspace.findFirstChild<Part>("Target")   // `var` infers the type (Part?)
const int MAX_PLAYERS = 8                                // constant, must be initialised
```

- Every variable has a static type, written or inferred with `var`.
- Uninitialised declarations get the type's zero value (`0`, `0.0`, `false`, `""`, empty collection); a non-nullable Instance type must be initialised.
- Block scoping (like JS `let`). No hoisting, no `undefined`.

### 3.3 Null safety

Types are non-nullable by default. `T?` allows `null`.

```jds
Part? maybe = workspace.findFirstChild<Part>("Door")
maybe.transparency = 1          // compile error: maybe could be null

if (maybe != null) {
    maybe.transparency = 1      // ok: narrowed to Part inside the if
}
maybe?.destroy()                // optional chaining
Part door = maybe ?? fallback   // null coalescing
Part forced = maybe!            // assert non-null; throws NullError if it is null
```

### 3.4 Casts and type tests

```jds
Part p = thing as Part          // throws CastError if thing is not a Part
Part? q = thing as? Part        // null if not a Part
if (thing is Part part) {       // test + bind (Java pattern matching)
    part.anchored = true
}
string t = typeof(thing)        // "Part"
```

### 3.5 Generics

Functions, classes and interfaces can be generic. Bounds use `extends`.

```jds
func first<T>(List<T> items) T? {
    return items.length > 0 ? items[0] : null
}

class Pool<T extends Instance> {
    List<T> free = []

    func take() T? { return free.pop() }
    func give(T item) { item.parent = null; free.push(item) }
}

Pool<Part> bullets = Pool<Part>()
```

- User-defined generics are checked at compile time and erased at runtime.
- **Engine** generic methods whose type parameter is an Instance class receive the class at runtime
  (`findFirstChildOfClass<Humanoid>()`, `getService<RunService>()`), so they can actually filter by type.
- Type arguments are inferred where possible: `first(parts)` is `Part?`.

## 4. Functions

Go's `func` keyword; Java-style typed parameters; return type after the parameter list.

```jds
func add(int a, int b) int {
    return a + b
}

func log(string msg) {                         // no return type = void
    print(`[game] ${msg}`)
}

func spawnAt(Vector3 pos, Color3 color = Color3.RED) Part {   // default parameters
    Part p = Part()
    p.position = pos
    p.color = color
    p.parent = workspace
    return p
}
```

### 4.1 Multiple return values (Go)

```jds
func divmod(int a, int b) (int, int) {
    return a / b, a % b
}

int q, int r = divmod(7, 2)
var q2, r2 = divmod(9, 4)
_, int rest = divmod(5, 3)      // `_` discards
```

### 4.2 Arrow functions and closures (JS)

```jds
func(int) int double = (int x) => x * 2
part.touched.connect((BasePart other) => { print(other.name) })
List<int> evens = numbers.filter((int n) => n % 2 == 0)
```

Parameter types may be omitted in an arrow function when they can be inferred from the expected
type: `numbers.map(n => n * 2)`.

### 4.3 `defer` (Go)

Runs when the enclosing function returns, even on error. Useful for cleanup:

```jds
func cutscene() {
    camera.cameraType = CameraType.Scriptable
    defer camera.cameraType = CameraType.Custom
    playIntro()        // may wait or throw; camera is restored either way
}
```

## 5. Control flow

```jds
if (health <= 0) { die() } else if (health < 20) { warn() } else { heal() }

for (int i = 0; i < 10; i++) { ... }           // C/Java/JS style
for (Part p in workspace.getDescendantsOfClass<Part>()) { ... }   // for-in over List/Set
for (string key, int value in scores) { ... }  // for-in over Map
while (alive) { ... }

switch (state) {                                // no fall-through (Go)
    case GameState.Lobby: startCountdown()
    case GameState.Playing, GameState.Overtime: tick()
    default: {}
}

int bonus = streak > 5 ? 50 : 0
```

Operators: `+ - * / %` (int `/` is integer division), `** ` (power), `== != < <= > >=`,
`&& || !`, `& | ^ ~ << >>`, `+= -= *= /= %= ++ --`, `?:`, `?.`, `??`. Strings concatenate with `+`.
`==` is value equality for primitives, strings and math types, identity for Instances and objects.
There is no `===`.

## 6. Classes, interfaces and enums

### 6.1 Classes

```jds
class Enemy {
    int health = 100
    float speed
    Model model

    constructor(Model model, float speed = 8) {
        this.model = model
        this.speed = speed
    }

    func damage(int amount) {
        health -= amount
        if (health <= 0) model.destroy()
    }
}

class Boss extends Enemy {
    constructor(Model model) { super(model, 4); health = 1000 }
}

Enemy e = Enemy(workspace.findFirstChild<Model>("Goblin")!)   // `new` is optional
```

- Fields: `<type> <name> [= value]`. Members are public; prefix with `_` for private (enforced by the compiler).
- Single inheritance with `extends`. Scripts **cannot** extend engine Instance classes in v1 (use composition: wrap an Instance in a class).

### 6.2 Interfaces (Go-style, implicit)

A type satisfies an interface if it has the right methods — no `implements` clause needed.

```jds
interface Damageable {
    func damage(int amount)
}

func explode(Vector3 at, List<Damageable> targets) { for (var t in targets) t.damage(50) }
// Enemy and Boss are Damageable automatically.
```

### 6.3 Enums

```jds
enum GameState { Lobby, Playing, Overtime, Ended }
GameState state = GameState.Lobby
```

Engine enums (`Material`, `KeyCode`, `PartType`, …) come from core and work the same way.

## 7. Modules — `import` / `export`

Every `ModuleScript` is a module. Paths resolve **in the Instance tree**, not on disk.

```jds
// ModuleScript at ReplicatedStorage/Shop
export const int SWORD_PRICE = 100
export func buy(Player p, string item) bool { ... }
export class Inventory { ... }
```

```jds
// Any Script
import { buy, SWORD_PRICE } from "ReplicatedStorage/Shop"
import * as Shop from "ReplicatedStorage/Shop"
import { Players, RunService } from "game"        // engine services
import { Grid } from "./Grid"                       // relative to this script's parent
```

- A module runs once per DataModel; later imports get the cached exports (FR-SCR-1).
- Circular imports are a compile error.

## 8. Engine API from scripts

Globals available in every script: `game` (DataModel), `workspace`, `script` (this Script Instance),
`print`, `warn`, `error`, `math`, `time()`, `wait()`.

### 8.1 Naming

Script-facing names are **camelCase members, PascalCase types**, generated from core metadata:
the `Size` property is `part.size`, the `Touched` signal is `part.touched`, `FindFirstChild` is
`findFirstChild`. (The place file and Properties panel keep the PascalCase names.)

### 8.2 Instances

```jds
Part p = Part()                         // = Instance.new("Part")
p.size = Vector3(4, 1, 4)
p.anchored = true
p.parent = workspace

Model? m = workspace.findFirstChild<Model>("House")
Humanoid h = character.waitForChild<Humanoid>("Humanoid")   // waits until it exists
List<Instance> kids = folder.getChildren()
```

Children are **not** accessible as `workspace.House` — that cannot be type-checked. Use
`findFirstChild<T>(name)` (nullable) or `waitForChild<T>(name)`. Studio autocompletes child names.

### 8.3 Signals

```jds
Connection c = part.touched.connect((BasePart other) => { ... })
part.touched.once(other => print("first touch"))
BasePart hit = part.touched.wait()      // suspends until fired, returns the argument
c.disconnect()
```

`Signal<T>` is a generic engine type; the compiler checks the listener's parameter type against `T`.

### 8.4 Math types

`Vector2`, `Vector3`, `CFrame`, `Color3`, `UDim2`, `Ray` are immutable value types with operators:

```jds
Vector3 v = Vector3(1, 2, 3) * 2 + Vector3.UP
CFrame cf = CFrame(0, 5, 0) * CFrame.angles(0, math.rad(90), 0)
float d = (a.position - b.position).magnitude
```

(Operator overloading is reserved to engine value types.)

## 9. Concurrency — `wait`, `go`, events

Scripts are single-threaded with cooperative tasks (DESIGN §5: all game state lives on the main thread).

```jds
wait(2.5)                       // suspend this task for 2.5 s of game time; returns actual elapsed
wait()                          // until next Heartbeat

go spinForever(part)            // Go-style: start a new task, continue immediately
go () => {                      // also works with an arrow function
    wait(10)
    door.open()
}

Task t = go countdown(30)       // tasks can be cancelled
t.cancel()

after(5, () => explode())       // run once after a delay (= go { wait(5); explode() })
```

- `wait` and `Signal.wait` suspend only the current task; other scripts keep running.
- A task that runs too long without suspending is stopped with a `TimeoutError` (FR-SCR-6).
- Tasks started by a script are cancelled when the script is destroyed or Play stops.

## 10. Errors

Java/JS-style exceptions:

```jds
try {
    buy(player, "sword")
} catch (ShopError e) {
    warn(e.message)
} catch (e) {                   // catch-all, e is Error
    warn(`unexpected: ${e.message}`)
} finally {
    saving = false
}

throw Error("out of stock")
class ShopError extends Error {}
```

- An uncaught error ends **that task only** and is reported in Output with script path, line, column
  and stack trace (FR-SCR-4). The engine and other scripts keep running (NFR-5).
- Runtime errors: `NullError`, `CastError`, `IndexError`, `TimeoutError`, `DestroyedError` (using a destroyed Instance).

## 11. Standard library (v1)

Kept small; everything else comes from engine services.

| Area | Contents |
|------|----------|
| Globals | `print`, `warn`, `error`, `time`, `wait`, `after`, `typeof` |
| `math` | `abs floor ceil round sqrt pow sin cos tan atan2 min max clamp lerp rad deg random randomInt noise PI` |
| `string` methods | `length upper lower trim split replace contains startsWith endsWith indexOf substring format` |
| `List<T>` methods | `length push pop insert remove indexOf contains sort map filter reduce find forEach join slice` |
| `Map<K,V>` methods | `size get set has delete keys values clear` |
| `json` | `encode(any) string`, `decode(string) any` |

No `eval`, no reflection, no file, network or process access. Saving player data will come through
an engine service (`DataStoreService`, post-v1).

## 12. Implementation design

All in `jordan-scripting`, implementing core's `ScriptService`. No third-party dependency.

```
source ──► Lexer ──► Parser ──► AST ──► Binder & TypeChecker ──► Compiler ──► Bytecode ──► VM
                     (Pratt)            (uses engine type info            (per-module      (one per
                                         from ClassRegistry)               constant pool)   DataModel)
```

| Stage | Notes |
|-------|-------|
| **Lexer** | Hand-written; tracks line/column for every token; does semicolon insertion. |
| **Parser** | Recursive descent with Pratt parsing for expressions. Error recovery at statement boundaries so Studio can report *all* errors in a script, not just the first. |
| **Type checker** | Resolves names and imports, infers `var` and arrow parameter types, checks generics, narrows nullables in `if`/`is`. Engine types come from a `TypeProvider` built from `ClassRegistry` + `Property` metadata, so new modules' classes appear without any scripting changes. |
| **Compiler** | AST → compact stack bytecode with typed instructions (`IADD`, `FADD`, `GETPROP slot`…). Property access on engine types compiles to a direct `Property` handle (no string lookup at runtime). |
| **VM** | Interpreter loop in Java. Each task is a VM fiber with its own frame stack, so `wait`/`Signal.wait` just park the fiber — no OS or virtual threads needed. An instruction counter per resume enforces the time budget (FR-SCR-6). |
| **Scheduler** | Resumes parked fibers in the `POST_SIMULATION` phase (DESIGN §3.6); `wait()` with no argument resumes on the next Heartbeat. |
| **Bindings** | Generic: one native bridge reads `Property` metadata for get/set, methods registered by modules as `NativeFunction`s with declared JordanScript signatures. |

Compilation happens when a script's `Source` changes in Studio (for live diagnostics) and when Play
starts; compiled bytecode is cached by source hash. Exported games ship pre-compiled bytecode.

**Why a custom VM instead of compiling to JVM bytecode?** Cheap suspension (`wait` anywhere), exact
instruction budgets, and a sandbox by construction. A JVM-bytecode backend (via the `java.lang.classfile`
API) is a possible later optimisation for hot scripts, behind the same front-end.

### 12.1 Tooling

- **Studio script editor:** syntax highlighting from the lexer's token kinds; live error squiggles from the type checker; autocomplete from the type checker's scope + engine types; hover shows doc comments.
- **Language server (post-v1):** the same front-end wrapped in LSP so VS Code / IntelliJ can edit `.jds` files.
- **API docs** are generated from the `TypeProvider` and doc comments (NFR-9).

## 13. Grammar sketch (EBNF, informal)

```ebnf
program      = { import | statement | declaration } ;
import       = "import" ( "{" ident { "," ident } "}" | "*" "as" ident ) "from" string ;
declaration  = [ "export" ] ( varDecl | funcDecl | classDecl | interfaceDecl | enumDecl ) ;
varDecl      = [ "const" ] ( type | "var" ) ident [ "=" expr ]
             | type ident { "," type ident } "=" expr ;            (* multi-return *)
funcDecl     = "func" ident [ typeParams ] "(" [ params ] ")" [ returnType ] block ;
params       = param { "," param } ;
param        = type ident [ "=" expr ] ;
returnType   = type | "(" type { "," type } ")" ;
classDecl    = "class" ident [ typeParams ] [ "extends" type ] "{" { member } "}" ;
member       = varDecl | funcDecl | "constructor" "(" [ params ] ")" block ;
interfaceDecl= "interface" ident [ typeParams ] "{" { "func" ident "(" [ params ] ")" [ returnType ] } "}" ;
enumDecl     = "enum" ident "{" ident { "," ident } "}" ;
typeParams   = "<" ident [ "extends" type ] { "," ident [ "extends" type ] } ">" ;
type         = ( ident [ "<" type { "," type } ">" ]
               | "func" "(" [ type { "," type } ] ")" [ returnType ] ) [ "?" ] ;
statement    = varDecl | block | if | for | while | switch | try | "return" [ exprList ]
             | "break" | "continue" | "throw" expr | "defer" expr | "go" expr | expr ;
for          = "for" "(" ( [ varDecl ] ";" [ expr ] ";" [ expr ]
                         | type ident [ "," type ident ] "in" expr ) ")" statement ;
```

## 14. Open questions

| # | Question | Current answer |
|---|----------|----------------|
| S1 | Language name & extension | JordanScript, `.jds` |
| S2 | Can scripts subclass engine Instances (`class Coin extends Part`)? | No in v1; composition instead |
| S3 | Separate `int`/`float`, or one `number` like JS? | Separate (closer to Java/Go, catches bugs) |
| S4 | Semicolons optional? | Yes |
| S5 | Scripts stored in place file or as `.jds` files on disk? | In place file for v1; files later |
