# Early Technical Risk Validation — Execution Tracker

**Source plan:** the approved third technical validation document, dated 2026-11-10. This repository copy is the live execution checklist. A box is checked only when the stated evidence exists; a successful build is not a physical-device acceptance test.

## Current implementation checkpoint

- [x] Native Android Kotlin/Compose host exists in the repository.
- [x] GitHub Actions builds JVM unit tests and a debug APK.
- [ ] Current commit's workflow artifact uploaded and verified.
- [ ] Install and launch on the target physical phone; record device baseline.
- [ ] CAD proof host and operations.
- [ ] DXF round-trip proof.
- [ ] DWG-to-DXF conversion comparison.
- [ ] 3D device performance proof.
- [ ] Technical risk decision record.

## Task 1 — Reproducible Android proof-test host

- [x] Create the minimal native Android project and launchable Compose screen.
- [x] Add GitHub Actions workflow for JVM tests, debug APK build, emulator smoke test, and artifact upload.
- [ ] Verify the current commit's APK artifact is available.
- [ ] Install APK on the target phone and record model, Android version, RAM/storage conditions, install/launch results, and permission prompts.
- [x] Commit the reproducible Android host.

**Evidence:** commit `e33794b6c55b2250462febccc1d5a9f2c42e4d5a`. Workflow run: [42721991690](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42721991690). JVM unit tests and `assembleDebug` passed. The Android emulator smoke test was still running at the last check; no emulator pass is claimed yet.

## Task 2 — Basic CAD operations on the phone

- [ ] Write model tests for create/add/move/delete/layer toggle/save/reopen.
- [ ] Run tests and record initial failures.
- [ ] Implement disposable proof drawing model and Compose proof screen.
- [ ] Run CI, build APK, and install on the physical phone.
- [ ] Test approximately 50, 500, and 2,000 entities; record touch latency, viewport usability, crashes, and save/reopen integrity.
- [ ] Commit proof and result report.

## Task 3 — DXF import/edit/export

- [ ] Assemble fixtures for line, polyline, circle, arc, text, dimensions, blocks, layers, units, and unknown entities.
- [ ] Add deterministic round-trip tests.
- [ ] Evaluate Android-compatible libraries, entity coverage, file-size behavior, licensing, and maintenance.
- [ ] Implement proof adapter for the selected candidate.
- [ ] Verify output in an independent DXF viewer; document preserved, changed, unsupported, or lost entities.
- [ ] Commit support matrix and evidence.

## Task 4 — DWG-to-DXF conversion fidelity

- [ ] Obtain three legitimate representative DWG fixtures.
- [ ] Evaluate conversion routes for availability, license, cost, privacy, and limits.
- [ ] Convert all fixtures through viable candidates.
- [ ] Compare geometry, scale, units, dimensions, text, layers, blocks, and visual output.
- [ ] Select a route only if evidence meets fidelity and licensing requirements; otherwise defer.
- [ ] Commit comparison and decision.

## Task 5 — 3D performance on the target phone

- [ ] Evaluate SceneView/Filament compatibility, maintenance, and licensing.
- [ ] Create a representative majlis scene.
- [ ] Add JVM tests for scene and object/material state.
- [ ] Add frame-time and memory diagnostics.
- [ ] Run a 10-minute interaction test on the physical phone.
- [ ] Record FPS, memory, heat, battery, crashes, and recovery.
- [ ] Apply provisional threshold: at least 30 average FPS, no crash/ANR/sustained freeze; document repeated drops below 15 FPS for over one second.
- [ ] Decide enable/reduce/defer without blocking 2D or project workflows.
- [ ] Commit results and decision.

## Task 6 — Technical decision record

- [ ] Mark each risk PASS, CONDITIONAL, or DEFERRED with evidence links.
- [ ] Record selected libraries/routes, license constraints, limitations, and rejected options.
- [ ] Verify production architecture does not depend on deferred capabilities.
- [ ] Commit decision record and update architecture.

## Continuous execution rule

Continue to the next unchecked task after each gate. If a gate fails, fix the cause or document a defensible defer decision, update this tracker, and continue with independent work. Never mark an untested physical-device or file-fidelity requirement as passed.
