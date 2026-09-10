# 🏥 MedNet

> A two-tier multi-agent system that couples territorial ambulance dispatch with in-hospital emergency department management.

![Java](https://img.shields.io/badge/Java-21-orange)
![Gradle](https://img.shields.io/badge/Gradle-9.7-blue)
![Jason](https://img.shields.io/badge/Jason-3.3.0-green)
![tuProlog](https://img.shields.io/badge/tuProlog-4.1.1-lightgrey)

<img width="2264" height="1785" alt="image" src="https://github.com/user-attachments/assets/09d14aca-e80d-497a-b942-496bf104fe89" />

## 📋 Overview

MedNet simulates an emergency network in which unpredictable calls trigger ambulance
dispatch and a network-level negotiation among hospitals for patient admission. Once a
patient is admitted, a second, local negotiation inside the hospital assigns a
specialized doctor. Capacity is never known a priori: beds fill up, walk-in patients
steal reservations, and equipment is contended, so agents must renegotiate while the
simulation is running.

The environment evolves through discrete simulation ticks across two coupled layers — a
territorial map holding ambulances and hospital sites, and one internal view per hospital
holding the triage queue, the doctors and the equipment.

## ✨ Main Features

- **Two-stage Contract Net Protocol** — a macro round between the control center and the
  hospitals (bidding on distance, occupancy and specialization match, weighted by
  severity code), and a micro round between a hospital's triage nurse and its doctors.
- **Priority triage with preemption** — a red-code patient can interrupt an ongoing
  lower-priority treatment; the victim is requeued at the head of its own priority class
  and later **replanned**, not restarted from scratch.
- **Mid-transport renegotiation** — if the assigned hospital loses capacity while the
  ambulance is en route, the network round reopens excluding it and the ambulance is
  rerouted.
- **STRIPS planning in Prolog** — each care plan is derived, not hardcoded: exams that
  need different machines are grouped into parallel stages, and replanning skips the
  exams already performed.
- **Mutual exclusion on critical equipment** — a Java monitor guards each machine, with
  an equipment-manager agent arbitrating a priority queue on top, plus grant revocation
  and lease expiry to prevent leaks.
- **RBAC enforced in Prolog** — every environment action is checked against a role
  policy (7 roles × 17 actions) before it executes.
- **Dual-view GUI** — a 30×30 territorial map alongside one live panel per hospital.

## 🏗️ Architecture

Three layers: BDI agents in AgentSpeak(L), a Java environment holding the simulated
world, and Prolog theories holding the domain knowledge.

| Agent | Count | Responsibility |
|---|---|---|
| `control_center` | 1 | Receives calls, dispatches the nearest free ambulance, runs the network-level CNP |
| `ambulance` | 3 | Moves on the map, performs on-site preliminary triage, transports, handles rerouting |
| `patient` | 5 | Environmental stimulus: reports an emergency with a pathology and an initial severity estimate |
| `hospital` | 3 | Bids for admissions, reserves and releases beds, requests diversion when saturated |
| `triage_nurse` | 3 | Secondary triage, priority queue with preemption, runs the in-hospital CNP |
| `doctor` | 6 | Bids on availability and specialization, executes the staged care plan |
| `equipment_manager` | 3 | Arbitrates access to the shared machines |

The default configuration (`mednet_local.mas2j`) runs **24 agents** over three hospitals:

| Hospital | Position | Beds | Specializations |
|---|---|---|---|
| `h1` | (5, 5) | 3 | cardiology, general |
| `h2` | (24, 24) | 2 | neurology, general |
| `h3` | (5, 24) | 2 | trauma_surgery, general |

Negotiation messages follow FIPA performatives — `cfp`, `propose`, `refuse`,
`accept_proposal`, `reject_proposal` — implemented once in `include/cnp_initiator.asl`
and `include/cnp_participant.asl` and reused by both stages.

## 🧠 Knowledge Base

Clinical and organizational constraints live in `src/main/prolog/mednet_kb.pl` and reach
the agents as AgentSpeak beliefs through the `mednet.prolog.consult_kb` internal action,
so plan context conditions query them directly rather than reading external data.

- **Severity codes** — `red`, `yellow`, `green`, `white`; preemptability is *derived*
  from urgency, not stated.
- **Pathologies** — `cardiac_arrest` → cardiology, `stroke` → neurology, `major_trauma`
  → trauma_surgery, `fracture` and `abdominal_pain` → general.
- **Equipment** — `ct_scanner`, `xray_room`, `ecg_station`, `lab`, `operating_room`
  (the latter required for red-code treatments only).

Three further theories complete the picture: `rbac.pl` (roles and permissions),
`strips.pl` (a generic STRIPS planner) and `care_domain.pl` (the care-plan domain and
the segmentation into parallel stages).

## 🚀 Getting Started

**Prerequisite:** JDK 21. The Gradle wrapper is included, so no local Gradle is needed.

```bash
# Run the simulation with the dual-view GUI
./gradlew runMednet_localMas

# Run the whole verification suite
./gradlew check
```

The simulation ends on its own once every patient has been discharged and every bed is
free again; the status bar reports the final tick.

The environment accepts arguments in `mednet_local.mas2j`:

| Argument | Effect |
|---|---|
| `seed=<n>` | Seeds the scenario generator (deterministic timelines) |
| `scenario=<name>` | `default`, `e2e`, `preemption`, `divert`, `severity`, `empty` |
| `period=<ms>` | Milliseconds per simulation tick (default 250) |
| `gui` | Opens the dual view |
| `manualClock` | Does not start the clock; ticks are driven programmatically |

## 🧪 Testing

Tests are split in two Gradle tasks; `check` runs both.

```bash
./gradlew test      # unit tests, excludes the "mas" tag
./gradlew masTest   # MAS integration tests, one JVM fork per class
```

**22 suites, 98 tests.** The unit layer covers the world model, the Prolog theories, the
STRIPS planner and the RBAC matrix. The MAS layer boots real Jason projects: five with
mocked agents to isolate single negotiation behaviours (network CNP, local CNP,
preemption, renegotiation, double-award) and four end-to-end runs of the full simulation
on deterministic scenarios.

## 📁 Project Structure

```
src/main/asl/          BDI agents (+ include/ for the reusable CNP roles)
src/main/java/mednet/
  env/                 Jason environment: percept routing, action execution, RBAC gate
  model/               Simulated world: territory, hospitals, patients, clock, scenarios
  plan/  prolog/       Internal actions bridging AgentSpeak to the Prolog theories
  rbac/  view/         Role parsing and the Swing dual view
src/main/prolog/       mednet_kb, rbac, strips, care_domain
src/test/              Unit tests, mocked agents, and MAS projects under mas2j/
```
