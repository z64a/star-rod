# Changelog

All notable changes to this project will be documented in this file.

## [0.11.0] - WIP

### Added

- Custom audio support, including sound banks, samples, instruments, sound effects, and ambient sequences
- Audio Booth for previewing music, sound effects, ambient sequences, and samples
- `-DumpAudio`, `-BuildAudio`, `-VerifyAudio`, and `-RenderAudio` commands for extracting, building, checking, and rendering audio to WAV
- (Map Editor) Feature extraction from decomp sources, including NPCs, entities, paths, camera settings, and texture panners
- (Map Editor) `-DumpMaps` and `-BuildMaps` commands. Build changed maps in one process without opening the editor
- (Map Editor) Customizable key bindings, light markers, and editable sprite shading profiles
- (Message Editor) Create, duplicate, rename, move, delete, and revert messages
- Theme designer with live previews and saved custom themes, available from each tool
- New startup/loading screens and credits, including third-party licenses

### Changed

- Read asset layers from `layout.yaml`, with `splat.yaml` as a fallback
- (Map Editor) Support DX maps stored beside their code as `map.xml`, with features in `features.json`. New maps can be assigned to an area, and `.meta` deletions are respected
- (Map Editor) Ported geometry, selection, painting, camera, and play-in-editor improvements from StarRodClassic. Added hex color entry, texture visibility while painting, and undoable brush settings
- (Message Editor) Browse messages directly, with filename filters, optional text previews, and an inspector showing their sources in the asset stack
- (Message Editor) Messages override individually. Mod files can contain only the messages they change, and duplicate definitions within a layer are rejected
- (Message Editor) Save edits to the existing mod definition's file, or use the source filename for a new override. Preserve file contents and message order
- (Message Editor) Replaced the right-side tabs with Text Variables, Image Variable, and Current Message sections
- Releases now include Java and launchers for Windows, Linux, and macOS. No separate Java installation is needed
- Updated Gradle and release packaging, added dependency verification, and removed the generated `.classpath` from version control
- `-Version` prints the version and build commit without starting the application

### Fixed

- (Map Editor) Fixed geometry cutting, vertex fusion/separation, triangle moves, copying, and undo/redo
- (Map Editor) Fixed threading issues between editing, rendering, and UI updates
- (Map Editor) Improved play-in-editor collision, stepping, jumping, and camera zones
- (Map Editor) Recovery files now keep unsaved markers and features
- (Message Editor) Report missing `[End]` and highlight errors in the message list and source inspector. Errors update when the message or selected source changes
- (Message/Image/World Map Editors) Fully undoing edits now clears the changed status
- (Message Editor) Detect external file changes before saving and roll back failed saves across multiple files
- (Message Editor) Faster filtering for large lists and support for messages without explicit names
- (World Map Editor) Fixed locations and paths flipping vertically after save/reload. Reload prompts for unsaved changes and keeps the current map if loading fails
- (Globals Editor) Reload only affects the selected table. Failed saves no longer mark edits as saved or replace their backups
- (Globals Editor) Fixed item target flags being copied from type flags and stale item details after reload
- (Image Editor) Prompt before New, Import, drag-and-drop, or Close discards edits. Cancelled or failed exports stop the operation
- (Image Editor) Fixed palette-import cancellation. Resizing, conversion, and palette changes now count as edits; changing the selection does not
- (Sprite Editor) Failed saves no longer clear undo/redo history
- Failed or cancelled saves no longer let editors close or reload and discard changes. Safer file writes preserve existing files when writing fails
- Fixed high-DPI rendering and picking, decomp enum parsing, and reading headers from WSL projects
- Extract message fonts even when another tool has already created the dump directory
- Fixed YAML files being left open and locked on Windows
- Fixed Nix builds and installation, including Linux ARM64 and macOS support

### Compatibility notes

- Texture panner maxima now use pixels, with a default of 128. The unit toggle only changes steps and offsets. For existing UV-mode `features.json` files, divide each panner's `max` by 1024. Legacy map XML keeps its original units

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
