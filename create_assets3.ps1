# BambooMod 1.20.1 アセット一括生成スクリプト (第4弾: 竹/たけのこ/稲/さくら系)
# 使い方: powershell -File create_assets3.ps1
$ErrorActionPreference = 'Stop'

$root = 'e:\mc\src\main\resources\assets\bamboomod'
$bsDir = "$root\blockstates"
$mbDir = "$root\models\block"
$miDir = "$root\models\item"
$langDir = "$root\lang"

foreach ($d in @($bsDir, $mbDir, $miDir, $langDir)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }

function Write-Json($path, $content) {
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  created: $(Split-Path -Leaf $path)"
}

Write-Host '=== 竹 (age 0-15) ==='
# blockstates: age 0-15 → 単一モデル (テクスチャは bamboo.png を縦に並べたクロスモデル)
$bambooVariants = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -le 15; $i++) {
    $bambooVariants.Add("        `"age=$i`": { `"model`": `"bamboomod:block/bamboo_stage`" }")
}
Write-Json "$bsDir\bamboo.json" ("{`n  `"variants`": {`n" + ($bambooVariants -join ",`n") + "`n  }`n}")

# クロスモデル(植物用)をカスタムボックスで表現: 旧版は細い柱なので cube ベースで自作
Write-Json "$mbDir\bamboo_stage.json" @'
{
  "parent": "minecraft:block/block",
  "textures": {
    "particle": "bamboomod:block/bamboo",
    "side": "bamboomod:block/bamboo2"
  },
  "elements": [
    { "from": [4, 0, 4], "to": [12, 16, 12], "faces": {
        "up":    { "texture": "#side" },
        "down":  { "texture": "#side" },
        "north": { "texture": "#side", "uv": [0, 0, 8, 16] },
        "south": { "texture": "#side", "uv": [0, 0, 8, 16] },
        "west":  { "texture": "#side", "uv": [0, 0, 8, 16] },
        "east":  { "texture": "#side", "uv": [0, 0, 8, 16] }
    } }
  ]
}
'@
Write-Json "$miDir\bamboo.json" '{
  "parent": "bamboomod:block/bamboo_stage"
}'

Write-Host '=== たけのこ (単一状態・交差型) ==='
Write-Json "$bsDir\bamboo_shoot.json" '{
  "variants": {
    "": { "model": "bamboomod:block/bamboo_shoot" }
  }
}'
Write-Json "$mbDir\bamboo_shoot.json" '{
  "parent": "minecraft:block/cross",
  "textures": {
    "cross": "bamboomod:block/bambooshoot"
  }
}'
Write-Json "$miDir\bamboo_shoot.json" '{
  "parent": "bamboomod:block/bamboo_shoot"
}'

Write-Host '=== 稲 (age 0-4) ==='
$riceVariants = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -le 4; $i++) {
    $riceVariants.Add("        `"age=$i`": { `"model`": `"bamboomod:block/rice_plant_stage_$i`" }")
}
Write-Json "$bsDir\rice_plant.json" ("{`n  `"variants`": {`n" + ($riceVariants -join ",`n") + "`n  }`n}")
for ($i = 0; $i -le 4; $i++) {
    Write-Json "$mbDir\rice_plant_stage_$i.json" @"
{
  "parent": "minecraft:block/crop",
  "textures": {
    "crop": "bamboomod:block/riceplant_stage_$i"
  }
}
"@
}
# 種アイテム
Write-Json "$miDir\riceseed.json" '{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "bamboomod:item/riceseed"
  }
}'

Write-Host '=== 桜の苗木 ==='
Write-Json "$bsDir\sakura_sapling.json" '{
  "variants": {
    "stage=0": { "model": "bamboomod:block/sakura_sapling" },
    "stage=1": { "model": "bamboomod:block/sakura_sapling" }
  }
}'
Write-Json "$mbDir\sakura_sapling.json" '{
  "parent": "minecraft:block/cross",
  "textures": {
    "cross": "bamboomod:block/sakura"
  }
}'
Write-Json "$miDir\sakura_sapling.json" '{
  "parent": "bamboomod:block/sakura_sapling"
}'

Write-Host '=== 桜の原木 ==='
Write-Json "$bsDir\sakura_log.json" '{
  "variants": {
    "axis=y":   { "model": "bamboomod:block/sakura_log" },
    "axis=z":   { "model": "bamboomod:block/sakura_log_horizontal", "x": 90 },
    "axis=x":   { "model": "bamboomod:block/sakura_log_horizontal", "x": 90, "y": 90 }
  }
}'
Write-Json "$mbDir\sakura_log.json" '{
  "parent": "minecraft:block/cube_column",
  "textures": {
    "end": "bamboomod:block/sakuralog_t",
    "side": "bamboomod:block/sakuralog_s"
  }
}'
Write-Json "$mbDir\sakura_log_horizontal.json" '{
  "parent": "minecraft:block/cube_column_horizontal",
  "textures": {
    "end": "bamboomod:block/sakuralog_t",
    "side": "bamboomod:block/sakuralog_s"
  }
}'
Write-Json "$miDir\sakura_log.json" '{
  "parent": "bamboomod:block/sakura_log"
}'

Write-Host '=== 桜の葉 ==='
Write-Json "$bsDir\sakura_leave.json" '{
  "variants": {
    "": { "model": "bamboomod:block/sakura_leave" }
  }
}'
# 旧 sakura_leave.json と同じく all=sakurapetal を使用 (sakura.png は苗木用)
Write-Json "$mbDir\sakura_leave.json" '{
  "parent": "minecraft:block/leaves",
  "textures": {
    "all": "bamboomod:block/sakurapetal"
  }
}'
Write-Json "$miDir\sakura_leave.json" '{
  "parent": "bamboomod:block/sakura_leave"
}'

Write-Host ''
Write-Host '=== 完了 ==='
