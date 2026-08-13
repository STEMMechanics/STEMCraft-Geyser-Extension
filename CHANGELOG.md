# Changelog

All notable changes to this project are documented here.

This project follows [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-08-13

### Added

- Configurable Bedrock scale-pivot correction for non-unit block-display scales.
- Live `px`, `py`, and `pz` calibration with save, reload, reset, and startup support.
- Wildcard block overrides with exact-match precedence and deterministic YAML-order fallback.
- Automatic migration of matching chest and cobblestone-wall entries to wildcard configuration.

### Changed

- Set the default global Z offset to `0.75`.

## [1.0.0] - 2026-08-13

### Added

- Resource-pack-backed Java block display rendering for Bedrock clients.
- Translation, scale, rotation and movement support.
- Live global and per-block calibration commands.
- Unified YAML configuration with legacy migration.
- Per-block scale and XYZ offset profiles.
- Reference-block calibration rig and runtime diagnostics.
