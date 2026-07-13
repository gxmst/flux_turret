# Flux Turret - Advanced Defense System

[English](#english) | [中文](#中文)

---

<a name="english"></a>

## English

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)
![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)
![Java 17](https://img.shields.io/badge/Java-17-blue.svg)
![Version](https://img.shields.io/badge/Version-1.5-blue.svg)
![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-green.svg)

**Flux Turret** is a Forge Energy-powered automated defense mod for Minecraft 1.20.1, inspired by the iconic defense structures from *Command & Conquer: Red Alert 2*. Build your fortress with intelligent turrets and a psychic beacon system!

### ✨ Key Features

- **🔋 Forge Energy Powered** - All turrets run on FE, compatible with tech mods
- **⚡ Tesla Coil** - High-voltage electric arcs that chain between enemies
- **🌈 Prism Tower** - Network-linked beam weapons with support bonuses
- **🔫 Gatling Turret** - Rapid-fire suppression that accelerates over time
- **💥 Grand Cannon** - Long-range artillery with parabolic trajectory
- **🧠 Psychic Beacon** - Mind control radar that attracts enemy waves
- **🧩 Upgrade Modules** - Install turret-specific combat modules by right-clicking matching turrets
- **🎮 GeckoLib Animations** - Smooth 3D models with dynamic effects
- **⚙️ Fully Configurable** - Customize all turret parameters

### 🎯 Turret Overview

| Turret | Type | Range | Damage | Energy | Special |
|--------|------|------:|-------:|-------:|---------|
| **Prism Tower** | Beam Weapon | 16.5-24 | 10 × (1 + supports × 0.35) | 1000 FE | Network support bonus |
| **Tesla Coil** | Electric Arc | 18.5 | 12 | 1400 FE | Overcharge mode available |
| **Gatling Turret** | Rapid Fire | 11 | 2/shot | 30 FE/shot | Spin-up acceleration |
| **Grand Cannon** | Artillery | 64 | 50 (AoE) | 8000 FE | Ignores line of sight |

### 🧩 Turret Upgrade Modules

Hold a module and right-click the matching turret to install it. Sneak-right-click is also supported for players who prefer a safer interaction.

| Module | Target | Effect |
|--------|--------|--------|
| **Fire Rounds** | Gatling Turret | Gatling hits ignite targets |
| **Chain Jump** | Tesla Coil | Tesla discharges jump to nearby enemies |
| **Remote Support** | Prism Tower | Expands support-link radius and effective scan range |
| **Seismic Shock** | Grand Cannon | Cannon blasts slow and weaken enemies in the impact area |

### 🧠 Psychic Beacon System

Build a pyramid structure beneath the beacon to activate it:

- **☀️ Day Mode** - Broadcasts selectable buff effects
- **🌙 Night Mode** - Attracts enemy mobs with randomized wave affixes
- **📦 Dawn Rewards** - Survive until dawn for scored supply crates and module drops
- **💎 Threat Levels** - 1-4 tiers based on pyramid size
- **⚡ Stability System** - Beacon explodes if stability reaches zero
- **📡 Defense Network View** - Active beacons draw links to nearby turret nodes
- **🧭 Doctrine Routes** - Switch between Guard, Lure, and Control doctrines from the beacon panel

**Wave Affixes:** Armored Assault, Swarm Front, Rush Infiltration, and Overload Night change enemy health, speed, damage, and wave size.

**Doctrine Routes:**
- **Guard** extends and strengthens beacon buffs.
- **Lure** increases pressure and improves reward output.
- **Control** applies slowing and weakness pressure around the beacon.

### 🔋 Energy Crystal

Portable FE storage block (100,000 FE capacity):

**Charging Methods:**
- Furnace smelting → Full charge
- Place above active furnace → 50 FE/t
- Right-click with redstone dust → 2,500 FE
- Right-click with redstone block → 22,500 FE
- Crafting with redstone block → Full charge

**Auto-Supply:** Place next to turrets for automatic power delivery (200 FE/t)

### 📦 Installation

1. Download from [Releases](../../releases)
2. Install [Forge 47.4.10](https://files.minecraftforge.net/) for Minecraft 1.20.1
3. Install [GeckoLib 4.8.3](https://www.curseforge.com/minecraft/mc-mods/geckolib)
4. Place `flux_turret-1.5.jar` in your `mods` folder
5. Launch the game and start building!

### 🎨 Visual Effects

**New in v1.5:**
- 🎨 Aggressive art overhaul branch with 2× block atlas resolution and 128px item icons
- 🧩 GeckoLib UV atlases upgraded in sync with higher-resolution textures
- 🛠️ Added extra model accent cubes for armor plates, muzzle parts, braces, rails, and energy cores
- ✨ New glow masks for Gatling Turret and Energy Crystal, wired through glowing render layers
- 🔬 Denser high-resolution surface detail: vents, scratches, secondary panel seams, circuit traces, and refined emissive areas
- 🧩 Added turret upgrade modules: Fire Rounds, Chain Jump, Remote Support, and Seismic Shock
- 🧠 Expanded Psychic Beacon gameplay with doctrines, wave affixes, battle scoring, and network-link visualization
- 🌐 Localized Psychic Beacon panel text and module/status messages
- 🛠️ Moved repeatable asset generation scripts into `tools/assets`

**Added in v1.4:**
- 🎨 Refined block textures with panel seams, rivets, warning stripes, and cleaner glow masks
- 🔥 Lightweight turret fire packets for smoother client-side firing visuals
- 🌀 Improved Gatling spin-up cadence and barrel animation
- 🧲 Optimized Psychic Beacon active-beacon lookups for sleep/death detection
- 🛡️ Safer comparator output handling for energy blocks

**Added in v1.3:**
- ⚡ Electric arcs with zigzag lightning effect (Tesla Coil)
- 🌈 Rainbow beam with intensity scaling (Prism Tower)
- 💨 Muzzle flash, smoke, and shell casings (Gatling)
- 💥 Enhanced explosions with shockwave rings (Grand Cannon)
- 📳 Screen shake for powerful weapons
- 🎵 Dynamic sound pitch based on turret state

### 🛠️ Development

**Requirements:**
- Minecraft 1.20.1
- Forge 47.4.10
- Java 17
- GeckoLib 4.8.3

**Build:**
```bash
./gradlew build
```

**Documentation:**
- [Changelog](docs/CHANGELOG.md)

### 📜 License

[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)

This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.

### 👤 Author

gxmst

---

<a name="中文"></a>

## 中文

**Flux Turret（高能防御炮塔）** 是一个面向 Minecraft 1.20.1 的 Forge Energy 驱动防御炮塔模组，灵感来源于《红色警戒 2》的经典防御建筑。使用智能炮塔和心灵信标系统打造你的防御堡垒！

### ✨ 核心特性

- **🔋 FE 能量驱动** - 所有炮塔使用 Forge Energy，兼容科技模组
- **⚡ 特斯拉线圈** - 高压电弧攻击，可连锁敌人
- **🌈 光棱塔** - 网络支援光束武器，支援越多越强
- **🔫 加特林炮塔** - 快速射击压制，持续开火加速
- **💥 巨炮** - 远程火炮，抛物线弹道
- **🧠 心灵信标** - 心灵控制雷达，吸引敌人波次
- **🧩 升级模块** - 手持模块右键对应炮塔即可安装专属战斗模块
- **🎮 GeckoLib 动画** - 流畅的 3D 模型和动态效果
- **⚙️ 完全可配置** - 自定义所有炮塔参数

### 🎯 炮塔概览

| 炮塔 | 类型 | 射程 | 伤害 | 能量 | 特点 |
|------|------|-----:|-----:|-----:|------|
| **光棱塔** | 光束武器 | 16.5-24 | 10 × (1 + 支援数 × 0.35) | 1000 FE | 网络支援加成 |
| **特斯拉线圈** | 电弧攻击 | 18.5 | 12 | 1400 FE | 可过载模式 |
| **加特林炮塔** | 快速射击 | 11 | 2/发 | 30 FE/发 | 转速加速 |
| **巨炮** | 火炮轰炸 | 64 | 50 (范围) | 8000 FE | 无视遮挡 |

### 🧩 炮塔升级模块

手持模块右键对应炮塔即可安装；如果你习惯防误触，潜行右键也可以安装。

| 模块 | 目标炮塔 | 效果 |
|------|----------|------|
| **燃烧弹** | 加特林炮塔 | 命中后点燃目标 |
| **链式跳跃** | 特斯拉线圈 | 放电会跳向附近敌人 |
| **远程支援** | 光棱塔 | 提升支援连接半径与有效扫描范围 |
| **震荡** | 巨炮 | 爆炸范围内敌人被缓慢并削弱 |

### 🧠 心灵信标系统

在信标下方搭建金字塔结构以激活：

- **☀️ 白天模式** - 广播可自选增益效果
- **🌙 夜晚模式** - 吸引带随机词缀的敌对波次
- **📦 黎明奖励** - 坚守到天明获得按评分生成的补给箱和模块奖励
- **💎 威胁等级** - 根据金字塔大小分为 1-4 级
- **⚡ 稳定系统** - 稳定度归零时信标爆炸
- **📡 防御网可视化** - 激活时会向附近炮塔节点绘制连接光束
- **🧭 信标路线** - 在面板中切换守护、诱敌、控制三种路线

**怪潮词缀：** 装甲突袭、集群压境、高速渗透、过载夜会改变敌人的血量、速度、伤害和波次数量。

**信标路线：**
- **守护** 扩大并强化信标增益。
- **诱敌** 提高怪潮压力，同时提升奖励产出。
- **控制** 对信标周围敌人施加缓慢和虚弱。

### 🔋 能量晶体

便携式 FE 存储方块（容量 100,000 FE）：

**充能方式：**
- 熔炉烧炼 → 满电
- 放置在燃烧的熔炉上方 → 50 FE/t
- 右键红石粉 → 2,500 FE
- 右键红石块 → 22,500 FE
- 与红石块合成 → 满电

**自动供电：** 放置在炮塔旁边自动供电（200 FE/t）

### 📦 安装方法

1. 从 [Releases](../../releases) 下载
2. 安装 [Forge 47.4.10](https://files.minecraftforge.net/) for Minecraft 1.20.1
3. 安装 [GeckoLib 4.8.3](https://www.curseforge.com/minecraft/mc-mods/geckolib)
4. 将 `flux_turret-1.5.jar` 放入 `mods` 文件夹
5. 启动游戏开始建造！

### 🎨 视觉效果

**v1.5 新增：**
- 🎨 激进美术重制分支：方块 atlas 分辨率提升 2 倍，物品图标统一到 128px
- 🧩 GeckoLib UV atlas 与高分辨率贴图同步升级
- 🛠️ 为模型追加装甲板、炮口件、支架、导轨和能量核心等细节 cube
- ✨ 新增加特林炮塔与能量晶体 glowmask，并接入发光渲染层
- 🔬 更密集的高分辨率表面细节：散热孔、划痕、二级面板线、电路纹和发光区域
- 🧩 新增炮塔升级模块：燃烧弹、链式跳跃、远程支援、震荡
- 🧠 扩展心灵信标玩法：路线、怪潮词缀、战后评分、防御网连线可视化
- 🌐 心灵信标面板与模块状态信息接入本地化
- 🛠️ 将可重复运行的美术生成脚本整理到 `tools/assets`

**v1.4 新增：**
- 🎨 方块贴图精修：面板分缝、铆钉、警示条与更干净的发光遮罩
- 🔥 新增轻量开火网络包，让客户端射击视觉更顺滑
- 🌀 优化加特林转速节奏和枪管动画
- 🧲 优化心灵信标活跃列表，用于睡眠阻止和击杀检测
- 🛡️ 能量方块红石比较器输出更安全

**v1.3 新增：**
- ⚡ 锯齿状闪电电弧效果（特斯拉线圈）
- 🌈 彩虹光束与强度缩放（光棱塔）
- 💨 枪口闪光、烟雾、弹壳（加特林）
- 💥 增强爆炸与冲击波环（巨炮）
- 📳 强力武器屏幕震动
- 🎵 基于炮塔状态的动态音效

### 🛠️ 开发

**环境要求：**
- Minecraft 1.20.1
- Forge 47.4.10
- Java 17
- GeckoLib 4.8.3

**构建：**
```bash
./gradlew build
```

**文档：**
- [更新日志](docs/CHANGELOG.md)

### 📜 许可

[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)

本作品采用 署名-非商业性使用-相同方式共享 4.0 国际 (CC BY-NC-SA 4.0) 协议进行许可。

### 👤 作者

gxmst

---

## 🎮 Quick Start / 快速开始

### Crafting Recipes / 合成配方

<details>
<summary>Click to expand / 点击展开</summary>

#### Prism Tower / 光棱塔
```
 D
AEA
III
```
D = Diamond / 钻石 | A = Amethyst Shard / 紫水晶碎片
E = Empty Crystal / 空晶体 | I = Iron Block / 铁块

#### Tesla Coil / 特斯拉线圈
```
 L
CRC
IDI
```
L = Lightning Rod / 避雷针 | C = Copper Block / 铜块  
R = Redstone Block / 红石块 | I = Iron Block / 铁块 | D = Diamond / 钻石

#### Gatling Turret / 加特林炮塔
```
 C
RDR
III
```
C = Crossbow / 弩 | R = Redstone / 红石  
D = Dispenser / 发射器 | I = Iron Ingot / 铁锭

#### Grand Cannon / 巨炮
```
 I
RDR
III
```
I = Iron Block / 铁块 | R = Redstone Block / 红石块  
D = Diamond Block / 钻石块

#### Psychic Beacon / 心灵信标
```
 R
DID
III
```
R = Redstone Block / 红石块 | D = Diamond / 钻石  
I = Iron Block / 铁块

#### Energy Crystal / 能量晶体
```
 D
DQD
 D
```
D = Diamond / 钻石 | Q = Quartz / 石英

</details>

---

## 🌟 Screenshots / 截图

*Coming soon / 敬请期待*

---

## 🤝 Contributing / 参与贡献

Contributions are welcome! Please feel free to submit issues and pull requests.

欢迎贡献！请随时提交问题和拉取请求。

---

## 📞 Contact / 联系方式

- GitHub: [gxmst](https://github.com/gxmst)
- Issues: [Report a bug](../../issues)

---

**Made with ❤️ for the Minecraft community**  
**为 Minecraft 社区用心打造**

