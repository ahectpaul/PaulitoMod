# AGENTS.md

This repository is a Fabric mod template for Minecraft 1.20.1 built with Fabric Loom and Java 17. It is intended to be extended into a custom mod for a home server running Prominence II.

## Project context

- Main build and dependency configuration: [build.gradle](build.gradle)
- Minecraft/Fabric versions: [gradle.properties](gradle.properties)
- Project setup notes: [README.md](README.md)
- Mod metadata and entrypoints: [src/main/resources/fabric.mod.json](src/main/resources/fabric.mod.json)
- Common mod entrypoint: [src/main/java/paulito/tutorialmod/net/PaulitoMod.java](src/main/java/paulito/tutorialmod/net/PaulitoMod.java)
- Client-only entrypoint: [src/client/java/paulito/tutorialmod/net/client/PaulitoModClient.java](src/client/java/paulito/tutorialmod/net/client/PaulitoModClient.java)

## Workflow for AI coding agents

- Keep the mod focused on a home-server use case for Prominence II, not generic template behavior.
- Put shared gameplay, commands, data, and server logic under the main source set: [src/main/java](src/main/java).
- Put rendering, HUD, client-only tweaks, and local UX features under the client source set: [src/client/java](src/client/java).
- Preserve the existing package structure unless the task explicitly requires a restructuring.

## Build and validation

Use the project’s standard Gradle commands:

- `./gradlew build` — compile and package the mod
- `./gradlew runClient` — run a dev client for testing client-side features
- `./gradlew runServer` — run a dev server for testing server-side features
- `./gradlew clean` — clear cached build outputs when needed

When changing gameplay logic, balance, commands, registries, or networking, validate with a build before considering the task complete.

## Conventions

- Keep the mod id as `paulitomod` unless the user explicitly asks for a rename.
- Prefer Fabric APIs and standard Minecraft patterns over custom workarounds.
- Use `ResourceLocation` and mod IDs consistently when registering content or assets.
- Keep mixins narrow and explainable; avoid broad, global changes.
- Respect source-set split: common logic belongs in the main mod, while client-only behavior stays in the client mod.
- If adding assets, follow Fabric resource conventions under [src/main/resources/assets](src/main/resources/assets).

## Scope guidance

This is a custom mod project, not a general-purpose template. Favor changes that support the home server experience, such as utilities, quality-of-life improvements, permissions-related features, custom commands, event handling, or small gameplay tweaks that fit the Prominence II server context.

Avoid unrelated refactors or large architecture changes without explicit instruction.
