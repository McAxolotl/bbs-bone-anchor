# BBS Bone Anchor

**English** | [简体中文](README.zh-CN.md) · v1.0.2

A client-side Fabric addon for BBS FS that adds per-bone, cross-model **Anchor** bindings. A bone on one Model Form can follow a form, bone, or body-part attachment on another Replay.

## Features

- Per-bone Anchor tracks, folded under the overall Anchor.
- Cross-model targets with native Anchor target picking, inheritance, and keep-transform editing.
- Body-part conversion to a Replay, with the former BodyPart parent relationship expressed as an Anchor keyframe. Supports both moving and copying the body part.
- Support for IK targets, bone constraints, physics, mirrored scales, and welded rendering.
- Isolated rigs when multiple Replays use the same model resource.
- Hierarchical ordering in both the timeline and the Form **Filter tracks** dialog.
- Session-only **Only show bound bones** filter in the Anchor track context menu.
- Localization for all 19 BBS FS languages.

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.16.14+
- Fabric API
- BBS FS 2.7
- Java 17+

## Usage

1. Open a film in the BBS replay editor.
2. Expand the root Model Form's **Anchor** track.
3. Open a per-bone row and select its target with the native Anchor editor.
4. Right-click the Anchor or a bone row to toggle **Only show bound bones**.
5. Open the replay editor's **Body parts** tree and right-click a body part:
   - **Convert to Replay** moves the nested form and its property tracks into a new Replay and removes the original BodyPart. Existing Anchor references in the current film that target that BodyPart or its descendants are automatically redirected to the new Replay.
   - **Copy as Replay** creates a new Replay while retaining the original BodyPart and its tracks. Existing Anchor references keep targeting the original, not the copy.
   - Both create an Anchor keyframe on the new Replay's root form, targeting the former bone or owner form. Its local transform and parent-local inheritance preserve the BodyPart binding as the parent moves. Hover over either action for the reference-handling explanation.

Per-bone tracks are stored internally as `anchor:<bone>` but display only the original bone name.

## Limitations

- Per-bone Anchor tracks are available on root Model Forms only.
- Body-part conversion requires an absolute-coordinate source Replay because the generated Anchor links to that Replay.
- Conversion preserves BodyPart inheritance in the parent form’s local space without baking the parent’s animation. Ancestor animation-state tracks targeting the subtree are migrated to its new paths; unrelated tracks remain on the original form.
- State definitions, duration and looping settings are preserved, but the new Replay triggers and plays them independently. Active playback phases are not transferred; random main-state selection, trigger order and blending may differ. Conflicting state IDs and custom trigger IDs are reassigned.
- Later retargeting uses the native Anchor editor's optional **Keep transform** setting. Non-finite local transforms reject conversion before the source changes.
- Conversion transfers form property tracks, including unknown tracks with a form path, and clears previously inactive root Anchor settings before creating the new keyframe.
- The new Replay copies category, enabled state, shadow settings, and looping. Source Replay tracks are copied only when **Use target** is enabled throughout the BodyPart’s ancestor chain, so the part actually uses the source Replay entity; otherwise, non-default recordable inputs of the actual independent BodyPart entity are saved as keys at the current cursor, without copying source Replay tracks. Migrated data is independent and does not sync subsequent edits.
- Label, name tag, action clips and Actor/first-person flags are not transferred. Entity age, swing phase, limb-animation history, CEM caches and third-party runtime state have no corresponding native Replay tracks; they are neither transferred nor shared live with the source.
- Bones directly controlled by active IK, physics, or constraints cannot be Anchor sources.
- CEM-driven bones are not supported as Anchor sources.
- Replays in absolute and relative coordinate modes cannot be linked.

## Changelog

### v1.0.2 — 2026-11-07

- Fix attachment inheritance through zero owner or model scales by evaluating bones before placement, without inverting the owner world frame.
- Retain temporarily unavailable bone targets; fall back to the owning form and resume bone following when the bone returns.
- Preserve recordable independent BodyPart inputs, including pose flags, orientation, velocity, extra variables and equipment, as independently editable Replay data.
- Migrate ancestor state tracks targeting the subtree and retain their state settings, with path/ID conflict handling, undo and reload coverage.
- Fix unconditional copying of source Replay tracks: respect **Use target** throughout the ancestor chain to identify the entity actually used by a nested BodyPart.
- Fix converted/copied BodyParts losing partial inheritance as their parent moves: preserve the binding and filter the bone in its parent form’s local space instead of storing a sampled world-space correction.
- Preserve this inheritance space through native Anchor serialization, interpolation and optional Keep transform editing.

### v1.0.1 — 2026-11-07

- Added **Convert to Replay** and **Copy as Replay** to the **Body parts** context menu, migrating the nested form and its property tracks and expressing the former parent relationship as an Anchor keyframe.
- Converting removes the original BodyPart and automatically redirects Anchor references to its subtree; copying retains the original and leaves existing references unchanged.
- Added hover explanations in all 19 languages and regression coverage for reference retargeting, track migration, parent frames, undo/redo and menu tooltips.

### v1.0.0 — 2026-11-05

#### Added

- Initial release.
- Per-bone cross-model Anchor tracks folded under the overall Anchor.
- Native Anchor target picking, inheritance, and keep-transform editing.
- Anchor runtime support for IK targets, bone constraints, physics, mirrored scales, and welded rendering.
- Isolated rigs when multiple Replays share the same model resource.
- Hierarchical ordering in both the timeline and the Form **Filter tracks** dialog.
- Session-only **Only show bound bones** filter in the Anchor track context menu.
- Localization for all 19 BBS FS languages.
- Regression and in-game smoke coverage.

## Build

```bash
./gradlew clean check build
./gradlew smokeTest
./gradlew smokeJar remapSmokeJar
```

`check` compiles the addon, runs the suites that need no game, and audits both the project and the packaged jar: mixin registration, all 19 localizations, Fabric metadata, source conventions and smoke wiring. `smokeTest` launches a dev client, runs the in-game suites and fails unless the report matches the expected counters; `smokeJar`/`remapSmokeJar` build the jars used to verify inside a production instance. The build resolves the latest compatible BBS FS release from Modrinth.

## License

MIT — see [LICENSE](LICENSE).
