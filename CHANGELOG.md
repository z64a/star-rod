# Changelog

All notable changes to this project will be documented in this file.

## [0.10.2] - 2025-07-21

### Fixed
- (Sprite Editor) Fixed Set Raster commands clearing when duplicated
- Proper asset stack texture handling
- Various minor bugfixes

## [0.10.1] - 2025-04-01

### Fixed
- (Sprite Editor) Imported sprites from old versions now load rasters properly
- (Sprite Editor) Component rendering is now sorted in the correct order
- (Map Editor) Push block grid indices are now saved

## [0.10.0] - 2025-03-13

### Added
- (Sprite Editor) Undo/redo for all actions
- (Sprite Editor) Popup window for selecting rasters
- (Sprite Editor) Timing information listed for commands in command list
- (Sprite Editor) Error highlighting for commands/components/etc
- (Map Editor) A wide array of import formats are now supported, including fbx and gltf
- (World Map Editor) Undo/redo for all actions

### Changed
- (Sprite Editor) UI reorganized and improved
- (Sprite Editor) New animations tab for easier animation/component list editing
- (Sprite Editor) More intuitive UI for binding assets to rasters
- (Sprite Editor) Proper keyframe support
- (Sprite Editor) Faster sprite loading
- Version checking will not open windows from command line and can be skipped
- Dark theme used by default
- Faster startup

### Fixed
- (Map Editor) Importing/exporting
- (Map Editor) Camera target override toggles now save properly

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
