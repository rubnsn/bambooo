# AGENTS.md - BambooMod 1.20.1 開発ガイド

本ファイルは AI エージェント / 人間コラボレータが本リポジトリで作業する際の共通ルール。`docs/port-spec-overview.md` と併せて必読。

## プロジェクト概要

* **modid**: `bamboomod` / **Minecraft**: 1.20.1 / **Forge**: 47.4.10 / **Java**: 17 / **Mappings**: official 1.20.1
* **旧版**: `E:\mcmod` (1.10.2) を `E:\mc` (1.20.1) へ移植中。参照: `E:\forge119` (1.19.2プロトタイプ `meshi2`) / https://github.com/rubnsn/sakura (1.16.5)
* **メインクラス**: `src/main/java/ruby/bamboo/BambooMod.java` - DeferredRegister は `BambooMod.BLOCKS/ITEMS/BLOCK_ENTITIES/MENUS/PARTICLE_TYPES/CREATIVE_TABS` に集約。新規 `DeferredRegister` を自作しないこと(バス未接続でクラッシュ)。

## ドキュメント構成

* **進捗一覧 (Single Source of Truth)**: `docs/port-spec-overview.md` - ブロック/アイテム/エンティティ/システムの進捗を1ファイルで俯瞰。表のみを持ち、詳細は書かない。
* **詳細仕様**: `docs/port-spec-*.md` - 各要素の旧仕様/1.20.1移植設計/検証チェックリスト。進捗ファイルと重複させない。
  * `port-spec-broadleave.md` / `port-spec-sakura-broadleave.md` / `port-spec-rice-indlight.md` / `port-spec-millstone.md` / `port-spec-campfire.md` / `port-spec-huton.md` / `port-spec-springblock.md` / `port-spec-springwater.md` / `port-spec-slidedoor.md` / `port-spec-bamboofood.md` / `port-spec-bamboobow-arrows.md` / `port-spec-sack.md` / `port-spec-tudura-foldingfan.md` / `port-spec-katana.md` / `port-spec-enchant.md`(アーカイブ) / `port-spec-sakura-new-elements.md`
* **アーカイブ**: `docs/archive/port-spec-blocks-all.md` / `docs/archive/port-spec-items-systems.md` - 旧一覧は統合済み。編集しない。
* **更新ルール**: 仕様変更時は個別ファイルを更新し、 `port-spec-overview.md` の該当行の状況を同期。逆に進捗ファイルに詳細を書き足さない。

## ディレクトリ構成

```
src/main/java/ruby/bamboo/
  BambooMod.java
  core/init/ BambooBlocks.java BambooItems.java BambooBlockEntities.java BambooMenus.java BambooParticles.java BambooClientSetup.java
  block/ AndonBlock.java BambooPaneBlock.java BroadLeaveBlock.java CampfireBlock.java ... TatamiBlock.java
  block/entity/ CampfireBlockEntity.java MillStoneBlockEntity.java JPChestBlockEntity.java SlideDoorBlockEntity.java
  block/decoration/ DecorationBlock.java DecorationSlabBlock.java DecorationStairsBlock.java EnumDecoration.java
  item/ Straw.java Rawrice.java BambooFoodItem.java BambooFoods.java CampfireItem.java
  crafting/grind/ GrindManager.java  crafting/cooking/ CookingManager.java  crafting/BambooRecipes.java
  gui/ MillStoneMenu.java MillStoneScreen.java CampfireMenu.java CampfireScreen.java
  client/particle/ PetalParticle.java
src/main/resources/
  assets/bamboomod/ models/ textures/ particles/
  data/bamboomod/ loot_tables/ recipes/ worldgen/
```

## ビルド / 実行

```powershell
./gradlew build          # ビルド
./gradlew runClient      # クライアント起動 (workingDirectory run/)
./gradlew runServer      # サーバ起動 --nogui
./gradlew runData        # データ生成
```

* エンコーディング UTF-8 (`build.gradle:205`)。Java 17 ツールチェーン。
* `processResources` で `mods.toml` / `pack.mcmeta` の `${mod_id}` 等を展開。

## コーディング規約

* **登録**: `BambooMod.BLOCKS.register` + `ITEMS.register` で Block+BlockItem 同時登録。`BambooItems.addCreative` でクリエイティブタブに登録。`Tatami` / `BambooPane` のように meta 集約は独立ブロック化。
* **Block**: `BaseEntityBlock` 継承時は `getRenderShape` で `MODEL`/`INVISIBLE` を明示。`updateShape` / `getShape` / `rotate` / `mirror` は `public`。空シェイプは `Shapes.empty()`、乱数は `RandomSource`。
* **BE**: `WorldlyContainer` + `SidedInvWrapper`、破壊時 `onRemove` で `Containers.dropContents`。NBTは `saveAdditional`/`loadAdditional`。
* **BER**: `BambooClientSetup.java` で `BlockEntityRenderers.register`。INVISIBLE ブロックのアイテムは `BlockItem` + `BEWLR` で 3D 描画(暫定はフラット)。
* **RenderType**: `BambooClientSetup.onClientSetup` で `cutout` / `cutoutMipped` / `translucent` を登録。`IndLight` / `BroadLeave` の色は `RegisterColorHandlersEvent` で乗算。
* **Particle**: `BambooParticles` で `SimpleParticleType` 登録、 `RegisterParticleProvidersEvent` で `PetalParticle.Provider` 登録。
* **レシピ**: `GrindManager` / `CookingManager` はコード登録式静的Map。`BambooRecipes.register` で `FMLCommonSetupEvent` 後に登録。
* **スタイル**: 既存 `BambooBlocks.java:42` の Javadoc / `@StateIgnore` 等の旧互換コメントを維持。1行2000文字超は truncate されるため分割。

## 連携ルール (エージェント向け)

* 作業前に `docs/port-spec-overview.md` で対象の状況を確認。未移植か済かで詳細docを参照。
* 作業の状態などの情報はAGENT_LOGに記述し、他のエージェントが編集しないようにさせること。
* 自分の作業している場所以外のエラーや例外などを修正するときは、必ずユーザーに確認を取ること。
* 進捗更新時は `port-spec-overview.md` の表1行のみを更新し、詳細は該当 `port-spec-*.md` に追記。両方に同じ詳細を書かない。
* 新規ブロック/アイテム追加時は `BambooBlocks` / `BambooItems` の登録順がクリエイティブタブ表示順になることに注意。`addCreative` 忘れで `AIR` スタック混入→タブクラッシュ。
* コミット前に `git status` / `git diff` を確認。`AGENTS.md` / `docs/` のみを編集し、`run/` `build/` はコミットしない。
* 質問や判断が必要な場合は `docs/port-spec-overview.md` の「要確認事項」や個別docの「注意点」を参照し、不明点はユーザーに確認。
