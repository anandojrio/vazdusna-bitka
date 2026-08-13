# Vazdusna Bitka

A distributed air-battle simulation in Java that combines concurrent aircraft execution, Java RMI, TCP socket coordination, shared radar state, and missile-based combat decisions across multiple cooperating processes.

## Overview

This project simulates two opposing command structures operating on a shared combat grid. Each side controls aircraft that can patrol, return to base, detect nearby objects through a radar service, and attack enemy aircraft based on last known position. The system is split into independently running processes that communicate through Java RMI and TCP sockets, making it a strong example of stateful distributed coordination under concurrency.

## Architecture

The simulation is composed of five main applications:

- `RadarApplication` — shared radar service exposed through Java RMI.
- `BlueCommandCenterApplication` — BLUE-side command center and operator interface.
- `RedCommandCenterApplication` — RED-side command center and operator interface.
- `BlueSquadroonApplication` — BLUE squadron process hosting aircraft threads.
- `RedSquadroonApplication` — RED squadron process hosting aircraft threads.

The command centers coordinate patrol, return, and attack commands. Squadron processes manage aircraft lifecycle and movement. The shared radar tracks aircraft and missile positions and returns nearby objects within radar range. Missile outcomes are propagated back into the system through dedicated command-center communication links.

## Core Concepts

- Independent long-running processes instead of a single monolith.
- Concurrent aircraft simulation with per-aircraft execution flow.
- Shared state access through Java RMI.
- Command and event propagation through TCP socket channels.
- Spatial tracking of aircraft and missiles on a continuous grid.
- Event-driven kill confirmation and enemy notification.

## Simulation Model

The battlefield is represented as an 8x8 grid, with finer-grained position updates inside each cell. Two sides, BLUE and RED, operate from opposite base coordinates and launch patrols into assigned zones. Aircraft move step-by-step, report their location to radar, observe nearby objects within range, and relay detections back to their command center.

Missiles are launched from base toward the target's last known position. A missile continues its path while scanning for the intended target; if the target is detected in range, the hit is confirmed, otherwise the missile self-destructs after reaching the final known destination.

## Aircraft Types

| Type | Side | Radar Range | Notes |
|---|---|---|---|
| F-15 | BLUE | 2.0 | Standard BLUE patrol aircraft |
| F-22 | BLUE | 3.5 | Higher detection range |
| SU-30 | RED | 2.0 | Standard RED patrol aircraft |
| MiG-31 | BOTH | 3.5 | Shared high-range aircraft type |

## Aircraft Lifecycle

Aircraft operate through a small state machine:

- `IN_BASE` — idle at base and not visible to radar.
- `PATROLLING` — actively moving within an assigned patrol zone.
- `RETURNING` — traveling back to home base.
- `DESTROYED` — removed from the active simulation after a confirmed hit.

This state-based model keeps command processing explicit and makes concurrency easier to reason about when multiple aircraft and missiles are active simultaneously.

## Communication Flow

1. A command center issues a patrol, return, or attack command.
2. The squadron process forwards that command to the relevant aircraft thread.
3. Aircraft update movement and register new positions with the shared radar service.
4. Radar returns nearby contacts within detection range.
5. Aircraft report sightings back to the command center.
6. If an attack is initiated, a missile is launched from base toward the target's last known position.
7. A successful hit triggers enemy command-center notification over a dedicated TCP link.
8. The enemy side destroys the targeted aircraft and removes it from active execution.

## Tech Stack

- Java 23.
- Java RMI for the shared radar service.
- TCP sockets for command-center and squadron communication.
- Jackson for JSON-based messaging.
- Multi-threaded execution for aircraft and missile workflows.
- Thread pools for launcher coordination.

## Project Structure

This codebase is organized around process roles and domain-specific behavior:

- Command-center logic for user-facing control and inter-process coordination.
- Squadron logic for aircraft creation, command handling, and state transitions.
- Radar service logic for position registration and proximity scans.
- Shared message/domain models for communication payloads.
- Missile execution logic for attack progression and hit detection.

## How to Run

Start the applications in this order:

1. `RadarApplication`
2. `BlueCommandCenterApplication`
3. `RedCommandCenterApplication`
4. `BlueSquadroonApplication`
5. `RedSquadroonApplication`

## Example Commands

```text
SHOW
P1 PATROL A2 B3
P2 PATROL 0.0 2.0 1.0 3.0
P1 RETURN
P1 ATTACK C3
```

## What This Project Demonstrates

- Designing a distributed simulation with multiple independently running services.
- Combining concurrency and networking in a stateful Java system.
- Coordinating shared visibility through remote service calls.
- Modeling combat behavior with explicit state transitions and event flow.
- Translating a simulation domain into clean process boundaries and communication protocols.

## Possible Next Improvements

- Add a system diagram showing process links and message directions.
- Add sample console output or an execution walkthrough.
- Add tests around state transitions and missile-hit scenarios.
- Add containerized startup or launch scripts for easier reproduction.
