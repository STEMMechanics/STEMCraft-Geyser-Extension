# Contributing to STEMCraft Geyser Extension

Thanks for your interest in contributing.

Bug reports, documentation fixes, suggestions and focused pull requests are
welcome. Open an issue before starting a substantial feature or structural
change so the approach can be discussed first.

## Development setup

Requirements:

- Java 21
- Git
- A compatible Geyser test server
- A Bedrock client for visual verification

Build and verify the project:

```shell
./gradlew clean build
```

Install the resulting JAR from `build/libs/` into a non-production Geyser
instance. Test changes with both Java and Bedrock clients where relevant.

## Pull requests

- Keep one main concern per pull request.
- Explain the problem and the chosen solution.
- Link the relevant issue where applicable.
- Include exact Geyser, Java and Bedrock versions used for testing.
- Include screenshots or video for rendering changes.
- Preserve existing configuration compatibility where practical.
- Update documentation and default configuration when behavior changes.
- Do not include generated packs, build output or local server data.

Changes involving entity metadata, resource-pack Molang, custom properties or
Geyser core APIs should be tested after a full server restart and a fresh
Bedrock resource-pack download.

## Coding style

- Target Java 21.
- Prefer clear, narrowly scoped methods.
- Keep configuration values validated and backward compatible.
- Avoid depending on unstable Geyser core methods when a stable equivalent exists.
- Add comments for protocol or Bedrock-renderer workarounds.

## Security

Do not report security vulnerabilities publicly. Follow [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contribution may be distributed under the
project's MIT License. You must have the right to submit all contributed code
and assets.
