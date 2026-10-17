Splash display fonts
===================

Seven font families are bundled as compact TrueType subsets supplied by the
official Google Fonts CSS API. The binaries are used as delivered; Star Rod
does not modify, rename or register them as system fonts. They contain only
the letters, spaces and punctuation needed by the splash title variants and the
compact Loading / Please wait banners.

All seven families use the SIL Open Font License 1.1. Their individual copyright
notices and full licenses are included in the adjacent *-OFL.txt files.
SOURCES.txt records the exact download URLs and upstream license sources.
Retrieved 2026-10-17. These font licenses are separate from the code license.

Bungee: David Jonathan Ross
Lobster: Impallari Type / The Lobster Project Authors
Rye: Nicole Fally / The Rye Project Authors
Monoton: Vernon Adams / The Monoton Project Authors
Rubik Mono One: Hubert and Fischer / The Rubik Mono One Project Authors
Caveat: The Caveat Project Authors
Press Start 2P: The Press Start 2P Project Authors

Subset request: https://fonts.googleapis.com/css2?family=FAMILY:wght@WEIGHT&text=STAR%20RODstarrod%5B%5D.%2F%2ALOADINGloadingPLEASE%20WAITplease%20wait
Use weight 700 for Caveat and 400 for the other families. Request TrueType
(the CSS response to curl's default user agent), not WOFF or WOFF2.
If adding title characters, refresh these subsets and run the glyph-coverage
test. Logical Java fonts provide a fallback if a font resource is unavailable.
