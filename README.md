# Air Battle Simulation

A distributed air battle simulation implemented in Java using concurrent programming, Java RMI, and TCP socket servers.

## Overview

Two opposing sides — **BLUE** (base at A1) and **RED** (base at H8) — operate on an 8×8 grid (A1–H8) with 0.1-unit resolution inside each cell. Each side runs three separate processes: a Command Center, a Squadron, and a shared Radar service used by both sides.

## Architecture

| Process | Description |
|---|---|
| `BlueCommandCenterApplication` | BLUE command center (port 5000), user interface and coordination |
| `RedCommandCenterApplication` | RED command center (port 5001), user interface and coordination |
| `BlueSquadroonApplication` | 5 BLUE aircraft threads (P1–P5), American aircraft |
| `RedSquadroonApplication` | 5 RED aircraft threads (C1–C5), Russian aircraft |
| `RadarApplication` | Shared radar service exposed via Java RMI |

**Technologies:** Java 23, Java RMI (radar), TCP sockets (CC ↔ squadron), Jackson (JSON messaging), thread pools (missile launchers).

## Aircraft Types

| Type | Side | Patrol Step | Return Step | Max Pause (ms) | Radar Range |
|---|---|---|---|---|---|
| F-15 | BLUE | 1.0 | 1.0 | 600 | 2.0 |
| F-22 | BLUE | 0.8 | 0.8 | 400 | 3.5 |
| SU-30 | RED | 1.0 | 1.0 | 600 | 2.0 |
| MiG-31 | BOTH | 1.4 | 1.4 | 200 | 3.5 |

## Aircraft States

- **IN_BASE** — aircraft is idle at home base, not tracked by radar  
- **PATROLLING** — aircraft moves within the assigned patrol zone, reports position to radar every step  
- **RETURNING** — aircraft flies back to base, unregisters from radar on arrival  
- **DESTROYED** — aircraft is shot down and removed from the simulation  

## Radar

All flying objects (aircraft and missiles) register their position with the shared Radar via RMI after each movement step. The Radar returns a list of objects within Euclidean radar range. Aircraft report detected contacts back to their Command Center over the socket connection.

Missiles use a small radar range (0.6 units) and only scan for **AIRCRAFT** objects. A hit is confirmed when the target aircraft appears in the missile's radar scan.

## Missiles

Missiles are launched from the base position (A1 or H8) toward the last known target position. Each missile:

- Moves toward the last known target position at `MissileConfig.STEP` per tick  
- Scans for the target aircraft using its small radar on every step  
- Reports **HIT** if it detects the target aircraft in range  
- Reports **SELF_DESTRUCTED** if it reaches the last known position without detecting the target  

Each side has 3 base launchers with a total of 15 missiles. A launcher is released for reuse only after its missile finishes its run.

## Kill Notification

When a missile hits a target, the attacking Command Center sends a `KillNotification` to the enemy Command Center over a dedicated kill-link TCP connection. The enemy CC then sends a `DESTROY` command to the aircraft, which terminates its thread.

---

## How to Run

Start the four processes **in this order**:

1. **Radar** (shared by both sides)  
   - Run: `RadarApplication`

2. **Blue Command Center**  
   - Run: `BlueCommandCenterApplication`  
   - Listens on port 5000 (squadron), 6000 (kill-link)

3. **Red Command Center**  
   - Run: `RedCommandCenterApplication`  
   - Listens on port 5001 (squadron), 6001 (kill-link)

4. **Blue Squadron**  
   - Run: `BlueSquadroonApplication`  
   - Aircraft IDs: P1–P5

5. **Red Squadron**  
   - Run: `RedSquadroonApplication`  
   - Aircraft IDs: C1–C5

---

## Commands

Both command centers accept the same command format. Replace `P?` with a BLUE aircraft ID (P1–P5) or `C?` with a RED aircraft ID (C1–C5).

| Command | Example | Description |
|---|---|---|
| `SHOW` | `SHOW` | Display the 8×8 grid with all known aircraft positions |
| `<id> PATROL <cell1> <cell2>` | `P1 PATROL A2 B3` | Order aircraft to patrol the zone between two grid cells |
| `<id> PATROL <x1> <y1> <x2> <y2>` | `P2 PATROL 0.0 2.0 1.0 3.0` | Same as above but with numeric coordinates |
| `<id> RETURN` | `P1 RETURN` | Order aircraft to return to base (A1 for BLUE, H8 for RED) |
| `<id> ATTACK <targetId>` | `P1 ATTACK C3` | Launch a missile from base toward the last known position of the target |
