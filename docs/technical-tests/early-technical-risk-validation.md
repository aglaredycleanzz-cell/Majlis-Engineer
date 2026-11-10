# Early Technical Risk Validation — Execution Tracker

**Source plan:** approved third technical validation document dated 2026-11-10. A box is checked only when evidence exists; build success is not physical-device acceptance.

## Task 1 — Reproducible Android proof-test host
- [x] Create native Android Kotlin/Compose host.
- [x] Add CI for JVM tests, debug APK build, and APK artifact upload (without emulator dependency).
- [x] Verify version 0.5.0 APK artifact and stable signing certificate (run #28).
- [ ] Install on target phone and record device baseline and launch result.
- [x] Commit reproducible host.

**Evidence:** host commit e33794b6c55b2250462febccc1d5a9f2c42e4d5a. Workflow run [42723055744](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42723055744) passed JVM unit tests and debug APK build, then failed only because the Android emulator did not boot before timeout. Emulator removed from CI in commit 713465d470bdfb87bf73e823a873d72a2b8d8abc. Runs [#18](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42724827764) and [#19](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42729020851) used different ephemeral debug signatures. Run [#20](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42730184967) passed the fixed test-key check, JVM tests, APK build, signature verification, and artifact upload. The checked-in key is development-only and must never be reused for a production/release signing key.

**Runner runtime maintenance:** GitHub announced the Node 20 retirement for JavaScript-based Actions. The workflow now uses Node 24-compatible action majors: checkout v6, setup-java v6, setup-gradle v6, and upload-artifact v6. Java remains 17 and Gradle remains 8.9; this is a runner-action runtime upgrade, not an Android toolchain upgrade. Run #21 completed all steps successfully with no Node 20 warnings.

## Task 2 — Basic CAD operations on phone
- [x] Define model behavior tests for create/add/move/edit/delete/layer visibility/snapshot/reopen.
- [x] Run model unit tests in CI, including independent 50/500/2,000-entity snapshots and invalid-geometry guards (run #28).
- [x] Implement the first project-scoped 2D drawing editor backed by Room and a 2→3 migration.
- [x] Build version 0.5.0 (versionCode 5), verify its stable debug signature, and upload the APK (run #28).
- [ ] Install on the physical phone and confirm launch/navigation into the drawing editor.
- [ ] Test approximately 50, 500, and 2,000 entities on the phone; record touch latency, viewport usability, crashes, and save/reopen integrity.
- [ ] Commit proof and result report.

**Evidence:** Base model/test commit 59305f5c90a9aa7e051c5d8e6c0fc69e28b5ce42. **Evidence:** The integrated editor was added in [24f7dc3](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/commit/24f7dc3e4454d5331bc567398ce1d48b48cb6d26); the cumulative-size test and geometry guards were fixed in [2ad1b2b](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/commit/2ad1b2b095df9c7e2314964e4d656631de515db3), counter/zoom rendering in [5b588ef](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/commit/5b588ef85a56703c9e56ab4c8b33a49f596a6089), the actual floating-point overflow case in [ba66557](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/commit/ba66557d33f4b4886706a2cad6e3d6a0bd89c606), and Arabic text rendering in [24ea29a](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/commit/24ea29a31756a6e051620d95d65ef5e57393609b). Run [#28](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42740499110) completed successfully: JVM tests, APK build, signature verification, artifact upload, and cleanup all passed. App version 0.5.0 / versionCode 5. Artifact ZIP SHA-256: 22b15f76c3b294b7ae20011e1605451f62bca2ba7895f8bcc821ca94571e43c1. The editor supports line/rectangle/circle/Arabic-text items, layers, selection, drag move, edit/delete, zoom, pan, and local Room persistence. Physical-device use and CAD file round-trip fidelity remain unverified.

## Task 3 — DXF import/edit/export
- [ ] Assemble fixtures for line, polyline, circle, arc, text, dimensions, blocks, layers, units, and unknown entities.
- [ ] Add deterministic round-trip tests.
- [ ] Evaluate Android libraries, entity coverage, file-size behavior, licensing, and maintenance.
- [ ] Implement proof adapter for selected candidate.
- [ ] Verify output independently and document preserved, changed, unsupported, or lost entities.
- [ ] Commit support matrix and evidence.

## Task 4 — DWG-to-DXF conversion
- [ ] Obtain three legitimate representative DWG fixtures.
- [ ] Evaluate routes for availability, license, cost, privacy, and limits.
- [ ] Convert fixtures and compare geometry, scale, units, dimensions, text, layers, blocks, and visuals.
- [ ] Select only a route meeting fidelity and licensing requirements; otherwise defer.
- [ ] Commit comparison and decision.

## Task 5 — 3D performance on target phone
- [ ] Evaluate SceneView/Filament compatibility, maintenance, and licensing.
- [ ] Create representative majlis scene and state tests.
- [ ] Add frame-time and memory diagnostics.
- [ ] Run 10-minute interaction test on physical phone.
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
Continue to the next unchecked task after each gate. If a gate fails, fix the cause or document a defensible defer decision, update this tracker, and continue independent work. Never mark an untested physical-device or file-fidelity requirement as passed.
