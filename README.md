# Star Rod

[![Release](https://img.shields.io/github/v/release/z64a/star-rod)][releases]
[![Download](https://img.shields.io/github/downloads/z64a/star-rod/total)][download]
![Build Status](https://img.shields.io/github/actions/workflow/status/z64a/star-rod/validate.yml)
[![#star-rod channel in the Star Haven Discord][discord-badge]][discord]

A suite of tools for editing assets from the [Paper Mario (2000) decompilation](https://github.com/pmret/papermario).

Star Rod supports Windows, Linux, and macOS.

**[Download Star Rod][download]**

Release archives include a platform-specific Java runtime. Extract the complete archive, then run `StarRod.bat` on Windows or `StarRod` on Linux and macOS; no separate Java installation is required.

To get started with modding Paper Mario, see [docs.starhaven.dev](https://docs.starhaven.dev/tools/decomp/setup.html).

[discord]: https://discord.gg/star-haven
[discord-badge]: https://img.shields.io/discord/279322074412089344?color=%237289DA&logo=discord&logoColor=ffffff&label=%23star-rod
[papermario-repo]: https://github.com/pmret/papermario
[releases]: https://github.com/z64a/star-rodx/releases
[download]: https://github.com/z64a/star-rod/releases/latest

## Development

### Splash screens

Startup splashes are composed locally from bundled stock photography with randomized
collages, color treatments, and varied Star Rod lettering. Open **Splash Screens...** in the
main window to preview compositions, reroll, and save an **Unhinged** intensity from
0 to 100 (default 55). The setting is also stored as `SplashUnhinged` in `main.cfg`.
Startup never downloads photos or waits for the composition to finish.
Please-wait and ROM-validation windows use a compact 480 × 144 banner with the same
photos, fonts, and Unhinged setting. Their lettering varies between “Loading” and
“Please wait,” above the status and progress bar.

Run `./gradlew previewSplashes` to render a comparison sheet at intensities 0, 55,
and 100 in `build/splash-preview`, plus sheets of font treatments and compact banners.
The title is usually `STAR ROD`, `Star Rod`, or `star rod`, with a combined 6% chance
of punctuation variants such as `[ star rod ]` and `star.rod`.
Photo credits, source links and license information
are bundled in `src/main/resources/splash/photos/`; these photos use the Pexels
License, separately from the source code license. Add new photos to `catalog.tsv`
and keep JPEG dimensions at or below 800 pixels per side and each file under 80 KiB.
The seven bundled display fonts use the SIL Open Font License; their notices and
source URLs are in `src/main/resources/splash/fonts/`. They are small, provider-supplied
TrueType subsets containing only splash-title characters.

### Setup

Star Rod is written in [Java](https://dev.java/) and uses the build tool [Gradle](https://gradle.org/). We recommend [Visual Studio Code](https://code.visualstudio.com/) as your editor.

To set up Star Rod for local development, follow these instructions:

1. Clone this repo, e.g. `gh repo clone z64a/star-rod`
2. Open it in [Visual Studio Code](https://code.visualstudio.com/): `code star-rod`
3. At the bottom right hand corner, a prompt will appear to install the recommended Visual Studio Code extensions; click **Yes**. This will install the Java and Gradle editor extensions. You can see the Gradle elephant icon in the Activity Bar.
4. Open the Explorer. Alternatively, press <kbd>Ctrl+Shift+E</kbd> / <kbd>⇧⌘E</kbd>.
5. Navigate to `src/main/java/app/StarRodMain.java`.
6. Click the "Run" button above the line declaring the `main` method (this type of button is called a [CodeLens](https://code.visualstudio.com/blogs/2017/02/12/code-lens-roundup)):

https://github.com/z64a/star-rod/blob/110ea7d6268f98a2bf565880572203bd066a9c1f/src/main/java/app/StarRodMain.java#L66

### Creating a ZIP locally

Run `./gradlew createReleaseZip`. The archive in `build/release` includes the runtime and launcher for the current operating system and CPU architecture.
