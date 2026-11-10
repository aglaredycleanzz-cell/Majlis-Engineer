# Early Technical Risk Validation — Execution Tracker

**Source plan:** approved third technical validation document dated 2026-11-10. A box is checked only when evidence exists; build success is not physical-device acceptance.

## Task 1 — Reproducible Android proof-test host
- [x] Create native Android Kotlin/Compose host.
- [x] Add CI for JVM tests, debug APK build, and APK artifact upload (without emulator dependency).
- [ ] Verify current commit's APK artifact and stable signing certificate.
- [ ] Install on target phone and record device baseline and launch result.
- [x] Commit reproducible host.

**Evidence:** host commit e33794b6c55b2250462febccc1d5a9f2c42e4d5a. Workflow run [42723055744](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42723055744) passed JVM unit tests and debug APK build, then failed only because the Android emulator did not boot before timeout. Emulator removed from CI in commit 713465d470bdfb87bf73e823a873d72a2b8d8abc. Runs [#18](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42724827764) and [#19](https://github.com/aglaredycleanzz-cell/Majlis-Engineer/actions/runs/42729020851) succeeded but used different debug signatures because hosted runners generated ephemeral debug keystores. A fixed test-only debug keystore is now checked into the public repository to make subsequent debug builds update-compatible. This key is for development APKs only; never reuse it for a production/release signing key. The next CI run must verify its signature and artifact.

## Task 2 — Basic CAD operations on phone
- [x] Define model behavior tests for create/add/move/edit/delete/layer visibility/snapshot/reopen.
- [ ] Run tests and record initial failures.
- [ ] Implement disposable proof drawing model and Compose proof screen.
- [ ] Run CI, build APK, and install on physical phone.
- [ ] Test approximately 50, 500, and 2,000 entities; record touch latency, viewport usability, crashes, and save/reopen integrity.
- [ ] Commit proof and result report.

**Current code:** ProofDrawingModel.kt and ProofDrawingModelTest.kt committed in 59305f5c90a9aa7e051c5d8e6c0fc69e28b5ce42. The model test run is not yet verified.

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
