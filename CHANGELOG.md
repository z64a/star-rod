# Changelog

All notable changes to this project will be documented in this file.

## [0.9.4] - 2025-02-21

### Fixed
- (Map Editor) libGL loading for Mix
- (Map Editor) EnemyTerritory generation now compatible with C++

## [0.9.3] - 2025-02-07

### Added
- (Sprite Editor) Copy/paste/duplicate for commands/keyframes
- Nix compatibility

### Changed
- Main config and logs moved to system user directory (%AppData% on Windows, etc)

### Fixed
- (Map Editor) Fixed crash when fusing vertices
- (Map Editor) (MacOS) Fixed mouse capture when accessibility permissions are not enabled
- (Sprite Editor) Copy animation works properly
- (Sprite Editor) Reordering current animation or component no longer freezes the editor
