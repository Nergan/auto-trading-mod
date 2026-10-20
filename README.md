# Auto Trading

[По-русски](README.ru.md)

Client-only Fabric mod for Minecraft 26.3. The character walks a saved route: takes items from input containers, trades them to selected villagers, and puts the result into output containers. The status line stays in English. Notices and the settings screen follow the game language.

## Loaders

| Minecraft | Loader | In this project |
| --- | --- | --- |
| 26.3 | Fabric | yes |
| 26.3 | NeoForge | no |
| 1.21.1 | Fabric / NeoForge | no |

There is no dedicated-server logic. On a dedicated server the mod is skipped (`environment: client`) and the server still starts.

## Download

A push to `main` updates one GitHub Release page: [auto trading for minecraft 26.3 fabric](https://github.com/Nergan/auto-trading-mod/releases/tag/minecraft-26.3-fabric). There is no Modrinth page. `all-jars.zip` on that page contains every jar in the table.

| File | Required | What it is |
| --- | --- | --- |
| `autotrading-fabric-26.3-1.0.0.jar` | yes | this mod |
| `fabric-api-0.162.0+26.3.jar` | yes | [Fabric API](https://modrinth.com/mod/fabric-api) |
| `fabric-language-kotlin-1.14.2+kotlin.2.4.21.jar` | yes | [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) |
| `cloth-config-fabric-26.3.159.jar` | yes | [Cloth Config](https://modrinth.com/mod/cloth-config) |
| `modmenu-21.0.0.jar` | no | [Mod Menu](https://modrinth.com/mod/modmenu), the button that opens the config screen |

Do not install `autotrading-fabric-26.3-1.0.0-sources.jar`.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Java 25
- Fabric API `0.162.0+26.3`
- Fabric Language Kotlin `1.14.2+kotlin.2.4.21` or newer
- Cloth Config `26.3.159` (`cloth-config`)
- Mod Menu `21.0.0` is optional. It only adds the config button. Without it the mod still runs, and the last saved `config/autotrading.json` is used.

## Install

Put `autotrading-fabric-26.3-1.0.0.jar` into the `mods` folder of a Fabric 26.3 instance, next to Fabric API, Fabric Language Kotlin, and Cloth Config. Add Mod Menu if you want the settings screen.

## How to use

The panel starts as `autotrading: disabled`. Press `T` to open chat: that frees the mouse, and the panel stays above the chat. The buttons only receive clicks while the mouse is free.

1. Click `set trading`. The status becomes `autotrading: setting input containers...`, and the other button becomes `cancel`. A counter under the status shows how many containers are stored.
2. Right-click each input container so its screen opens. A repeat click on the same one does not raise the counter. Placing a block does not count, and opening your own inventory does not count. Hold something that is not a block, or an empty hand. Click `apply`.
3. The status becomes `autotrading: setting trade villagers...`. Right-click each villager, then `apply`. The trade screen still opens.
4. The status becomes `autotrading: setting output containers...`. Right-click each output container, then `apply`. The status returns to `autotrading: disabled`, and the button returns to `set trading`.
5. `cancel` at any of these steps drops the draft. A route saved earlier stays. `apply` does nothing while the other button says `set trading`, and it also does nothing until the current step has at least one target.
6. Click the status line `autotrading: disabled`. It becomes `autotrading: enabled` and both buttons lock. Click `autotrading: enabled` to turn it off. The **Run the route** switch in Mod Menu does the same thing. The character walks the containers and villagers: it takes the configured items, buys the configured offer, and puts the configured results into the output containers in order. It stops at the first output that can take what is left.
7. While the route is running, movement, jump, sneak, attack, use, drop, inventory, and the hotbar are locked. Chat, the command key, the player list, screenshots, and fullscreen stay available. `Esc` opens the pause menu, the status becomes `autotrading: stop`, and the route waits until that menu closes. Death turns the route off as well.

An empty input or a villager who is out of stock makes the character wait. A missing path, a full output, a missing target, or a bad item id shows a notice and plays a bell. The mod writes nothing to chat and adds no commands.

## Config

`config/autotrading.json`, edited through Mod Menu when Cloth Config and Mod Menu are installed.

| Option | Default |
| --- | --- |
| Run the route | off |
| Show the panel | on |
| Panel corner | `top-right` (`top-left`, `bottom-left`, `bottom-right`) |
| Items to take | `minecraft:melon` |
| Trade | 1 `minecraft:melon` for 1 `minecraft:emerald` |
| Second cost | empty |
| Items to put away | `minecraft:emerald` |

The trade matches the villager's discounted price, the number drawn without a strikethrough. A different count is not bought. An out-of-stock offer waits. An empty input waits. Full outputs, a missing target, a missing path, death, and a bad item id show a notice and play a bell. Nothing is written to chat, and the mod adds no commands.

## License

MPL-2.0. See [LICENSE](LICENSE).
