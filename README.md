# bosstag_editor

See [DESIGN.md](DESIGN.md)

## Repository layout

- `mod/` — the Fabric mod and its Gradle project
- `language-server/` — planned language server
- `vscode-extension/` — planned VS Code extension

The repository-level Gradle wrapper is shared by the Gradle projects. Build
the mod from the repository root with:

```sh
./gradlew build
```

To target the mod explicitly, use `./gradlew :mod:build`.
