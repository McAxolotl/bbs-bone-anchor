# BBS Bone Anchor

**English** | [简体中文](README.zh-CN.md) · v1.0.0

A client-side Fabric addon for BBS FS that adds per-bone, cross-model **Anchor** bindings. A bone on one Model Form can follow a form, bone, or body-part attachment on another replay actor.

## Features

- Per-bone Anchor tracks, folded under the overall Anchor.
- Cross-model targets with native Anchor target picking, inheritance, and keep-transform editing.
- Support for IK targets, bone constraints, physics, mirrored scales, and welded rendering.
- Isolated rigs when multiple actors use the same model resource.
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

Per-bone tracks are stored internally as `anchor:<bone>` but display only the original bone name.

## Limitations

- Per-bone Anchor tracks are available on root Model Forms only.
- Body-part attachments keep native BBS behavior. Use an independent replay actor when an attachment must be driven across models.
- Bones directly controlled by active IK, physics, or constraints cannot be Anchor sources.
- CEM-driven bones are not supported as Anchor sources.
- Actors in absolute and relative coordinate modes cannot be linked.

## Build

```bash
./gradlew clean check build
./gradlew smokeTest
./gradlew smokeJar remapSmokeJar
```

`check` compiles the addon, runs the suites that need no game, and audits both the project and the packaged jar: mixin registration, all 19 localizations, Fabric metadata, source conventions and smoke wiring. `smokeTest` launches a dev client, runs the in-game suites and fails unless the report matches the expected counters; `smokeJar`/`remapSmokeJar` build the jars used to verify inside a production instance. The build resolves the latest compatible BBS FS release from Modrinth.

## License

MIT — see [LICENSE](LICENSE).
