// Fonts are embedded as data URIs: the app pages load from file://, where Chromium's CORS-mode
// font fetches fail, and bundling them keeps the launcher looking right offline.
import inter from '@fontsource-variable/inter/files/inter-latin-wght-normal.woff2?inline';
import interExt from '@fontsource-variable/inter/files/inter-latin-ext-wght-normal.woff2?inline';
import orbitron600 from '@fontsource/orbitron/files/orbitron-latin-600-normal.woff2?inline';
import orbitron800 from '@fontsource/orbitron/files/orbitron-latin-800-normal.woff2?inline';

const LATIN_EXT = 'U+0100-02BA, U+02BD-02C5, U+02C7-02CC, U+02CE-02D7, U+02DD-02FF, U+0304, U+0308, U+0329, U+1D00-1DBF, U+1E00-1E9F, U+1EF2-1EFF, U+2020, U+20A0-20AB, U+20AD-20C0, U+2113, U+2C60-2C7F, U+A720-A7FF';

const faces = [
  new FontFace('Inter Variable', `url(${inter})`, { weight: '100 900', display: 'swap' }),
  new FontFace('Inter Variable', `url(${interExt})`, { weight: '100 900', display: 'swap', unicodeRange: LATIN_EXT }),
  new FontFace('Orbitron', `url(${orbitron600})`, { weight: '600', display: 'swap' }),
  new FontFace('Orbitron', `url(${orbitron800})`, { weight: '800', display: 'swap' })
];
for (const face of faces) document.fonts.add(face);
