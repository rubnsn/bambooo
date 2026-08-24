# BambooMod 1.20.1

Japanese-style decoration mod with bamboo, sakura and tatami blocks.
Minecraft 1.20.1 / Forge 47.4.10 / Java 17 / Mappings official.

- **modid**: `bamboomod` / **version**: `1.20.1-0.1` (see `gradle.properties:mod_version`)
- Porting from 1.10.2 (`E:\mcmod`) → 1.20.1 (`E:\mc`). Ref: `E:\forge119` (1.19.2 prototype `meshi2`), https://github.com/rubnsn/sakura (1.16.5)
- Main class: `src/main/java/ruby/bamboo/BambooMod.java`

## Build / Run

```powershell
./gradlew build
./gradlew runClient   # workingDirectory run/
./gradlew runServer --nogui
./gradlew runData
```

## License (簡易れどめ)

Dual license:

- **Code** (`src/main/java/**`, `src/main/resources/data/**`, `build.gradle` etc.): **MMPL-1.0.1** (Minecraft Mod Public License v1.0.1) — see [LICENSE](LICENSE) and https://spdx.org/licenses/MMPL-1.0.1.html
- **Assets** (`src/main/resources/assets/bamboomod/**` — models, blockstates, textures, lang, particles): **CC BY-NC 4.0** (Creative Commons Attribution-NonCommercial 4.0) — https://creativecommons.org/licenses/by-nc/4.0/deed.ja

SPDX: `MMPL-1.0.1 AND CC-BY-NC-4.0` / `gradle.properties:mod_license=MMPL-1.0.1` → `mods.toml:license` に展開。

- MODパック同梱（無償配布）は許可。資産の商用利用（販売・有料配布・商用AI学習等）は要許諾。
- バイナリ再配布時はソース入手手段を無償提供すること（MMPL §6）。
- `Derived code` (§5) は別ライセンス可。Minecraft本体の所有が前提 (§1)。無保証 (§2)。

Full texts: see [LICENSE](LICENSE).

## Docs

- 進捗一覧: `docs/port-spec-overview.md` (Single Source of Truth)
- 詳細仕様: `docs/port-spec-*.md`
- 開発ガイド: [AGENTS.md](AGENTS.md)

> Note: `docs/*` と `src/main/resources/assets/bamboomod/textures/**/*.png` は現在 `.gitignore` により git 管理外。公開時に含める場合は `.gitignore` を調整してください。
