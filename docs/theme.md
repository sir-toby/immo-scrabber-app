# Theme und Fachfarben

Entscheidung: [Branding und Theming (#20)](https://github.com/sir-toby/immo-scrabber-app/issues/20).

## M3-Farbschema (`core/ui/theme/Color.kt`)

- Seed: Favicon-Blau `#1B3A6B`.
- Erzeugt mit [`@material/material-color-utilities`](https://www.npmjs.com/package/@material/material-color-utilities) 0.4.0, der Bibliothek hinter dem Material Theme Builder.
- Variante `SchemeTonalSpot` (Standard des Theme Builders, „Color match“ aus), Kontrast 0, hell und dunkel.
- `primary` ist damit Ton 40 der Seed-Palette (`#425E91`), nicht das Favicon-Blau selbst. `SchemeFidelity` („Color match“ an) träfe den Seed nur als `primaryContainer` und machte `primary` fast schwarz (`#002452`), Tertiär braun.
- Das Favicon-Blau steht als `@color/brand_blue` für Icon und Splash bereit.

Neu erzeugen (Node ≥ 18, in einem leeren Ordner):

```sh
npm i @material/material-color-utilities@0.4.0 esbuild
# gen.mjs: siehe unten
npx esbuild gen.mjs --bundle --platform=node --outfile=gen.cjs && node gen.cjs
```

```js
import { argbFromHex, hexFromArgb, Hct, SchemeTonalSpot, MaterialDynamicColors as M } from '@material/material-color-utilities';
const seed = Hct.fromInt(argbFromHex('#1B3A6B'));
const roles = ['primary','onPrimary','primaryContainer','onPrimaryContainer','inversePrimary','secondary','onSecondary','secondaryContainer','onSecondaryContainer','tertiary','onTertiary','tertiaryContainer','onTertiaryContainer','background','onBackground','surface','onSurface','surfaceVariant','onSurfaceVariant','surfaceTint','inverseSurface','inverseOnSurface','error','onError','errorContainer','onErrorContainer','outline','outlineVariant','scrim','surfaceBright','surfaceContainer','surfaceContainerHigh','surfaceContainerHighest','surfaceContainerLow','surfaceContainerLowest','surfaceDim'];
for (const dark of [false, true]) {
  const s = new SchemeTonalSpot(seed, dark, 0.0);
  for (const r of roles) console.log(`private val ${r}${dark ? 'Dark' : 'Light'} = Color(0xFF${hexFromArgb(M[r].getArgb(s)).slice(1).toUpperCase()})`);
}
```

## Fachfarben (`core/ui/theme/ImmoColors.kt`, Zugriff über `MaterialTheme.immoColors`)

- **Bewertung:** `TonalPalette.fromInt` aus `#4CAF50` (interessant) bzw. `#F44336` (uninteressant). Hell: Ton 40 mit Ton 100 darauf, dunkel: Ton 80 mit Ton 20 darauf.
- **Energieklassen:** hell = die Web-Skala aus `frontend/src/style.css` des Backend-Repos (`A+` und `A` `#4CAF50`, `B` `#8BC34A`, `C` `#CDDC39`, `D` `#FFEB3B`, `E` `#FFC107`, `F` `#FF9800`, `G` `#FF5722`, `H` `#F44336`), Text schwarz wie im Web. Dunkel = in HCT gleicher Farbton, Chroma × 0,8, Ton − 10, begrenzt auf 50…80.

## Icon

`res/drawable/ic_launcher_foreground.xml` baut `frontend/public/favicon.png` (Haus mit Kamin und Smiley) als Vektor im 108-dp-Viewport nach, innerhalb der 66-dp-Sicherheitszone. Hintergrund: Ton 95 der Primär-Palette (`#EDF0FF`). `ic_launcher_monochrome.xml` und `ic_notification.xml` nutzen dieselbe Kontur mit ausgestanztem Smiley.
