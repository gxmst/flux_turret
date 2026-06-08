# Flux Turret - Advanced Defense System

[English](#english) | [中文](#中文)

---

<a name="english"></a>

## English

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)
![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)
![Java 17](https://img.shields.io/badge/Java-17-blue.svg)
![Version](https://img.shields.io/badge/Version-1.3-blue.svg)
![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-green.svg)

**Flux Turret** is a Forge Energy-powered automated defense mod for Minecraft 1.20.1, inspired by the iconic defense structures from *Command & Conquer: Red Alert 2*. Build your fortress with intelligent turrets and a psychic beacon system!

### ✨ Key Features

- **🔋 Forge Energy Powered** - All turrets run on FE, compatible with tech mods
- **⚡ Tesla Coil** - High-voltage electric arcs that chain between enemies
- **🌈 Prism Tower** - Network-linked beam weapons with support bonuses
- **🔫 Gatling Turret** - Rapid-fire suppression that accelerates over time
- **💥 Grand Cannon** - Long-range artillery with parabolic trajectory
- **🧠 Psychic Beacon** - Mind control radar that attracts enemy waves
- **🎮 GeckoLib Animations** - Smooth 3D models with dynamic effects
- **⚙️ Fully Configurable** - Customize all turret parameters

### 🎯 Turret Overview

| Turret | Type | Range | Damage | Energy | Special |
|--------|------|------:|-------:|-------:|---------|
| **Prism Tower** | Beam Weapon | 16.5-24 | 10 × (1 + supports × 0.35) | 1000 FE | Network support bonus |
| **Tesla Coil** | Electric Arc | 18.5 | 12 | 1400 FE | Overcharge mode available |
| **Gatling Turret** | Rapid Fire | 11 | 0.5/shot | 30 FE/shot | Spin-up acceleration |
| **Grand Cannon** | Artillery | 64 | 50 (AoE) | 8000 FE | Ignores line of sight |

### 🧠 Psychic Beacon System

Build a pyramid structure beneath the beacon to activate it:

- **☀️ Day Mode** - Broadcasts buff effects (Speed, Resistance, Strength)
- **🌙 Night Mode** - Attracts enemy mobs creating defensive waves
- **📦 Dawn Rewards** - Survive until dawn for supply crates
- **💎 Threat Levels** - 1-4 tiers based on pyramid size
- **⚡ Stability System** - Beacon explodes if stability reaches zero

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
4. Place `flux_turret-1.3.jar` in your `mods` folder
5. Launch the game and start building!

### 🎨 Visual Effects

**New in v1.3:**
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
- [Visual Effects Guide](docs/VISUAL_EFFECTS_OPTIMIZATION.md)
- [Blockbench Modeling Guide](docs/BLOCKBENCH_MODELING_GUIDE.md)

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
- **🎮 GeckoLib 动画** - 流畅的 3D 模型和动态效果
- **⚙️ 完全可配置** - 自定义所有炮塔参数

### 🎯 炮塔概览

| 炮塔 | 类型 | 射程 | 伤害 | 能量 | 特点 |
|------|------|-----:|-----:|-----:|------|
| **光棱塔** | 光束武器 | 16.5-24 | 10 × (1 + 支援数 × 0.35) | 1000 FE | 网络支援加成 |
| **特斯拉线圈** | 电弧攻击 | 18.5 | 12 | 1400 FE | 可过载模式 |
| **加特林炮塔** | 快速射击 | 11 | 0.5/发 | 30 FE/发 | 转速加速 |
| **巨炮** | 火炮轰炸 | 64 | 50 (范围) | 8000 FE | 无视遮挡 |

### 🧠 心灵信标系统

在信标下方搭建金字塔结构以激活：

- **☀️ 白天模式** - 广播增益效果（速度、抗性、力量）
- **🌙 夜晚模式** - 吸引敌对生物形成防御波次
- **📦 黎明奖励** - 坚守到天明获得补给箱
- **💎 威胁等级** - 根据金字塔大小分为 1-4 级
- **⚡ 稳定系统** - 稳定度归零时信标爆炸

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
4. 将 `flux_turret-1.3.jar` 放入 `mods` 文件夹
5. 启动游戏开始建造！

### 🎨 视觉效果

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
- [视觉效果指南](docs/VISUAL_EFFECTS_OPTIMIZATION.md)
- [Blockbench 建模指南](docs/BLOCKBENCH_MODELING_GUIDE.md)

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
 I
III
```
D = Diamond / 钻石 | I = Iron Block / 铁块

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

