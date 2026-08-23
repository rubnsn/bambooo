# BambooMod 1.20.1 アセット一括生成スクリプト (第2弾: デコ系/畳/indlight)
# 使い方: powershell -File create_assets.ps1
$ErrorActionPreference = 'Stop'

$root = 'e:\mc\src\main\resources\assets\bamboomod'
$bsDir = "$root\blockstates"
$mbDir = "$root\models\block"
$miDir = "$root\models\item"
$old = 'e:\mcmod\resources\assets\bamboomod'

foreach ($d in @($bsDir, $mbDir, $miDir)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }

function Write-Json($path, $content) {
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  created: $(Split-Path -Leaf $path)"
}

# デコ系テクスチャ名 → ブロック名対応
$decos = @('kawara','plaster','namako','kaya','cbirch','coak','cpine')

Write-Host '=== デコ系 (通常/スラブ/階段) ==='
foreach ($deco in $decos) {
    # --- blockstates ---
    Write-Json "$bsDir\$deco.json" (@"
{
  "variants": {
    "": { "model": "bamboomod:block/$deco" }
  }
}
"@)

    Write-Json "$bsDir\$($deco)_slab.json" (@"
{
  "variants": {
    "type=bottom": { "model": "bamboomod:block/$($deco)_slab" },
    "type=top": { "model": "bamboomod:block/$($deco)_slab_top" },
    "type=double": { "model": "bamboomod:block/$deco" }
  }
}
"@)

    # 階段はバニラ標準パターン
    $stairVariants = New-Object System.Collections.Generic.List[string]
    $shapes = @(
        @{ s='straight';   m="$($deco)_stairs" },
        @{ s='outer_left'; m="$($deco)_outer_stairs" },
        @{ s='outer_right';m="$($deco)_outer_stairs" },
        @{ s='inner_left'; m="$($deco)_inner_stairs" },
        @{ s='inner_right';m="$($deco)_inner_stairs" })
    foreach ($half in @('bottom','top')) {
        foreach ($facing in @('east','west','south','north')) {
            foreach ($shape in $shapes) {
                $y = switch ($facing) { 'east'{0}; 'south'{90}; 'west'{180}; 'north'{270} }
                $parts = @("`"model`": `"bamboomod:block/$($shape.m)`"")
                if ($half -eq 'top') { $parts += '"x": 180' }
                if ($y -ne 0) { $parts += "`"y`": $y" }
                $parts += '"uvlock": true'
                $stairVariants.Add("        `"facing=$facing,half=$half,shape=$($shape.s)`": { " + ($parts -join ', ') + ' }')
            }
        }
    }
    Write-Json "$bsDir\$($deco)_stairs.json" ("{`n  `"variants`": {`n" + ($stairVariants -join ",`n") + "`n  }`n}")

    # --- block models ---
    Write-Json "$mbDir\$deco.json" (@"
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "bamboomod:block/$deco"
  }
}
"@)

    Write-Json "$mbDir\$($deco)_slab.json" (@"
{
  "parent": "minecraft:block/slab",
  "textures": {
    "bottom": "bamboomod:block/$deco",
    "top": "bamboomod:block/$deco",
    "side": "bamboomod:block/$deco"
  }
}
"@)

    Write-Json "$mbDir\$($deco)_slab_top.json" (@"
{
  "parent": "minecraft:block/slab_top",
  "textures": {
    "bottom": "bamboomod:block/$deco",
    "top": "bamboomod:block/$deco",
    "side": "bamboomod:block/$deco"
  }
}
"@)

    foreach ($v in @('', '_inner', '_outer')) {
        $parent = switch ($v) { '' {'minecraft:block/stairs'} '_inner' {'minecraft:block/inner_stairs'} '_outer' {'minecraft:block/outer_stairs'} }
        Write-Json "$mbDir\$($deco)$($v)_stairs.json" (@"
{
  "parent": "$parent",
  "textures": {
    "bottom": "bamboomod:block/$deco",
    "top": "bamboomod:block/$deco",
    "side": "bamboomod:block/$deco"
  }
}
"@)
    }

    # --- item models ---
    Write-Json "$miDir\$deco.json" (@"
{
  "parent": "bamboomod:block/$deco"
}
"@)
    Write-Json "$miDir\$($deco)_slab.json" (@"
{
  "parent": "bamboomod:block/$($deco)_slab"
}
"@)
    Write-Json "$miDir\$($deco)_stairs.json" (@"
{
  "parent": "bamboomod:block/$($deco)_stairs"
}
"@)
}

Write-Host '=== 畳4種 + indlight16色 ==='
$tatamis = @('tatami', 'tatami_non_border', 'tatami_tan', 'tatami_tan_non_border')
# 畳のモデル名対応 (旧ブロックモデルを流用)
$tatamiModels = @{
    'tatami'               = 'tatami'
    'tatami_non_border'    = 'tatami_ns'
    'tatami_tan'           = 'tatami_tan'
    'tatami_tan_non_border'= 'tatami_tan_ns'
}
# モデル名 → テクスチャ名 (実在ファイル: tatami_x/tatami_nsx/tatami_tan_x/tatami_tan_nsx)
$tatamiTexs = @{
    'tatami'      = 'tatami_x'
    'tatami_ns'   = 'tatami_nsx'
    'tatami_tan'  = 'tatami_tan_x'
    'tatami_tan_ns' = 'tatami_tan_nsx'
}

foreach ($t in $tatamis) {
    $model = $tatamiModels[$t]
    # blockstate: facing(水平4方向)で回転
    Write-Json "$bsDir\$t.json" (@"
{
  "variants": {
    "facing=north": { "model": "bamboomod:block/$model", "y": 180 },
    "facing=east":  { "model": "bamboomod:block/$model", "y": 90 },
    "facing=south": { "model": "bamboomod:block/$model" },
    "facing=west":  { "model": "bamboomod:block/$model", "y": 270 }
  }
}
"@)

    # block model: 軸方向カラム(縦横同じテクスチャ)
    $tex = $tatamiTexs[$model]
    Write-Json "$mbDir\$model.json" (@"
{
  "parent": "minecraft:block/cube_column",
  "textures": {
    "end": "bamboomod:block/$tex",
    "side": "bamboomod:block/$tex"
  }
}
"@)

    # item model
    Write-Json "$miDir\$t.json" (@"
{
  "parent": "bamboomod:block/$model"
}
"@)
}

Write-Host '=== indlight 16色 ==='
$dyes = @('white','orange','magenta','light_blue','yellow','lime','pink','gray','silver','cyan','purple','blue','brown','green','red','black')
foreach ($dye in $dyes) {
    # facing(6方向) + 接続4方向。接続は当面単一モデル(facing回転のみ)
    Write-Json "$bsDir\indlight_$dye.json" (@"
{
  "variants": {
    "facing=up":    { "model": "bamboomod:block/indlight_$dye", "x": 90 },
    "facing=down":  { "model": "bamboomod:block/indlight_$dye", "x": 270 },
    "facing=north": { "model": "bamboomod:block/indlight_$dye", "y": 180 },
    "facing=east":  { "model": "bamboomod:block/indlight_$dye", "y": 270 },
    "facing=south": { "model": "bamboomod:block/indlight_$dye" },
    "facing=west":  { "model": "bamboomod:block/indlight_$dye", "y": 90 }
  }
}
"@)

    # block model (薄板ボックス)
    Write-Json "$mbDir\indlight_$dye.json" (@"
{
  "parent": "bamboomod:block/indlight_base",
  "textures": {
    "all": "bamboomod:block/indlight_$dye",
    "particle": "bamboomod:block/indlight_$dye"
  }
}
"@)

    # item model
    Write-Json "$miDir\indlight_$dye.json" (@"
{
  "parent": "bamboomod:block/indlight_$dye"
}
"@)
}

# indlight共通ベースモデル
Write-Json "$mbDir\indlight_base.json" @"
{
  "parent": "minecraft:block/block",
  "elements": [
    {
      "from": [2, 14, 2],
      "to": [14, 16, 14],
      "faces": {
        "up":    { "texture": "#all" },
        "down":  { "texture": "#all" },
        "north": { "texture": "#all" },
        "south": { "texture": "#all" },
        "west":  { "texture": "#all" },
        "east":  { "texture": "#all" }
      }
    }
  ]
}
"@

Write-Host ''
Write-Host '=== 完了 ==='
