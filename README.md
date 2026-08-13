# Vazdusna Bitka

A multi-process distributed air-battle simulation in Java, combining concurrent aircraft execution, Java RMI for shared radar state, TCP socket communication between processes, and a missile combat system with full lifecycle tracking.

## Overview

Two opposing sides — BLUE and RED — operate independent command structures on a shared battlefield. Each side runs its own command center and squadron process. Aircraft execute as concurrent threads, register their positions with a shared RMI radar service, scan for nearby contacts, and respond to operator commands. When an attack is ordered, a missile is launched from the command center, tracks the target's last known position, and propagates the result back to both sides.

The project is a practical study in distributed state coordination: five independent JVM processes synchronize through RMI and TCP sockets with no shared memory, making concurrency and message design the central engineering challenge.

## System Architecture

The simulation is composed of five independently runnable processes:

| Process | Role |
|---|---|
| `RadarApplication` | Boots the shared RMI radar service — the only globally visible state |
| `BlueCommandCenterApplication` | BLUE operator interface; issues commands, receives squadron reports, launches missiles |
| `RedCommandCenterApplication` | RED operator interface; mirrors BLUE with opposite side logic |
| `BlueSquadroonApplication` | Hosts BLUE aircraft threads; relays commands and sends position reports |
| `RedSquadroonApplication` | Hosts RED aircraft threads; mirrors BLUE squadron behavior |

The radar service is the sole shared distributed component. All other communication travels point-to-point over TCP sockets, serialized as JSON via Jackson.

## Communication Design

The system uses two distinct channels:

- **Java RMI** — aircraft and missiles call the remote `RadarService` to register positions (`RadarUpdateRequest`) and scan for nearby objects (`RadarScanResponse`, `RadarContact`). The radar is stateful and shared by both sides. The backing store is `AirObjectRegistry`, a thread-safe registry that holds `AirObjectState` entries for all active flying objects.
- **TCP sockets** — command centers and squadrons exchange typed messages: `CommandMessage` (operator → squadron), `SquadroonOutboundMessage` (squadron → command center), `MissileReportMessage` (missile progress), and `KillNotification` (cross-side kill events).

This separation is intentional: radar access is a remote shared service, while command and event flows are direct process-to-process channels.

## Domain Model

### Aircraft

Each aircraft runs as a dedicated thread inside its squadron process. Aircraft move through a state machine:

| State | Meaning |
|---|---|
| `IN_BASE` | Idle at home base, not visible on radar |
| `PATROLLING` | Moving within an assigned `PatrolBox` zone |
| `RETURNING` | Flying back to home base coordinates |
| `DESTROYED` | Removed from active execution after a confirmed kill |

Aircraft type is encoded in the `AircraftType` enum with per-type radar detection range. Both F-15 and F-22 serve the BLUE side; SU-30 and MiG-31 serve the RED side.

### Radar

The radar tracks all active flying objects through `AirObjectState` entries, identified by a `FlyingObjectType` that distinguishes aircraft from missiles. Each scan call returns a list of `RadarContact` objects within the caller's detection radius. Distance calculations are handled by `DistanceUtils` using Euclidean geometry. The grid coordinate system is managed by `BoardUtils`, which translates between human-readable grid notation (e.g. `A2`) and continuous floating-point positions.

### Missiles

Missiles are configured via `MissileConfig` and have their own execution lifecycle, tracked with `MissileStatus` (`IN_FLIGHT`, `HIT`, `MISSED`) and `MissileLaunchOrigin` to identify which side fired. A missile travels toward the target's last known `Position`, scanning for the target via the radar at each step. A confirmed hit triggers a `KillNotification` sent to the enemy command center.

## Combat Flow

```
Operator issues ATTACK command
        │
        ▼
CommandCenter → TCP → Squadron
        │
        ▼
Missile thread launched → moves toward target's last known Position
        │
        ├─ Missile scans radar (RMI) at each step
        │
        ├─ Target found in range → HIT
        │        │
        │        └─ KillNotification → enemy CommandCenter → aircraft DESTROYED
        │
        └─ Target not found at destination → MISSED → self-destruct
```

## Startup Order

Processes must be started in this order because later processes depend on the RMI registry being available:

```
1. RadarApplication
2. BlueCommandCenterApplication
3. RedCommandCenterApplication
4. BlueSquadroonApplication
5. RedSquadroonApplication
```

## Example Operator Commands

```
SHOW                          → display current aircraft status
P1 PATROL A2 B3               → send aircraft P1 to patrol zone A2–B3 (grid cell format)
P2 PATROL 0.0 2.0 1.0 3.0    → patrol with explicit coordinate bounds
P1 RETURN                     → order P1 back to base
P1 ATTACK C3                  → launch a missile at the last known position in zone C3
```

## Project Structure

```
src/main/java/org/example/
├── bootstrap/              # Five entry-point applications
├── common/
│   ├── config/             # MissileConfig — tunable missile parameters
│   ├── dto/                # All inter-process message types (8 classes)
│   ├── enums/              # AircraftState, AircraftType, FlyingObjectType,
│   │                       #   MissileLaunchOrigin, MissileStatus, Side
│   ├── model/              # AirObjectState, PatrolBox, Position
│   └── util/               # AnsiColors (console output), BoardUtils (grid parsing),
│                           #   DistanceUtils (Euclidean radar range)
├── radar/
│   ├── service/            # RadarService (RMI interface) + RadarServiceImpl
│   └── store/              # AirObjectRegistry — thread-safe radar state store
└── squadroon/
    ├── aircraft/           # Aircraft thread logic and state management
    └── missile/            # Missile execution and hit-detection logic
```

## Tech Stack

- Java 23
- Java RMI (remote radar service)
- TCP sockets (command and event channels)
- Jackson (JSON serialization for all messages)
- Maven (build and dependency management)
- Multi-threaded execution (per-aircraft and per-missile threads)

## Build

```bash
mvn clean package
```

Run each application from your IDE or via the compiled JAR, following the startup order above.

## What This Project Demonstrates

- Designing a distributed system with multiple independent processes and no shared memory
- Combining RMI and TCP as complementary communication mechanisms for different roles
- Modeling concurrent state transitions across many active threads (aircraft + missiles simultaneously)
- Using an `AirObjectRegistry` as a thread-safe shared store behind a remote service interface
- Separating domain concerns cleanly: radar, command, squadron, missile, messaging, and grid layers
- Coordinating cross-process kill events in a stateful real-time simulation
