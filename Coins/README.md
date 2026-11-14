# Coins

A Coins currency for Paper 26.2. Players earn **5 coins for every 1 minute online**, and spend them in the **Coin Shop** on **spawners** and **armor trims** priced by rarity. Coins are completely separate from your Vault/EssentialsX money.

## Build (GitHub Codespaces, same as your other plugins)

1. Create a new GitHub repo (e.g. `Coins`), upload everything in this folder, open it in a Codespace.
2. Make sure JDK 25 is active (`java -version` should say 25).
3. Run `mvn clean package`
4. Your jar is `target/Coins-1.0.0.jar`. Put it in `/plugins`, then restart the server.

PlaceholderAPI is needed for the scoreboard line (it is optional for everything else).

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/coins` | Show your balance | everyone |
| `/coins balance <player>` | Show another player's balance | `coins.balance.others` (default: everyone) |
| `/coins give <player> <amount>` | Give coins | `coins.admin` |
| `/coins take <player> <amount>` | Take coins | `coins.admin` |
| `/coins set <player> <amount>` | Set coins | `coins.admin` |
| `/coins reload` | Reload config.yml and shop prices | `coins.admin` |
| `/coinshop` (or `/cshop`) | Open the Coin Shop | `coins.shop` (default: everyone) |
| `/coinshop spawners` / `trims` | Open a category directly | `coins.shop` |

Players without `coins.earn` do not earn coins (default: everyone has it).

## TAB scoreboard (coins under money)

Open your TAB scoreboard config (the `lines:` list of your sidebar) and add the Coins line **directly under your Money line**:

```yaml
- "&6⛁ Coins: &6%coins_balance_formatted%"
```

Example of how it can sit in your list (keep your own lines and styling):

```yaml
lines:
  - "..."                                   # rank line
  - "...Money line using your money placeholder..."
  - "&6⛁ Coins: &6%coins_balance_formatted%"   # <- new, gold with gold coin icon
  - "..."                                   # online / kills lines
```

Then run `/tab reload`.

Placeholders: `%coins_balance%` (12500), `%coins_balance_formatted%` (12,500), `%coins_icon%`.

Notes on the icon: `⛁` is a normal Unicode symbol, so it shows in gold on Java with no resource pack. Some Bedrock clients may show it as a small box; if that happens change `currency.icon` in config.yml to another symbol you like (it is used everywhere: messages, GUI, `%coins_icon%`).

## Shop prices (all editable in config.yml)

Each rarity tier has one price, and the list of mobs/trims in that tier.

| Rarity | Spawner price | Spawners | Trim price | Trims |
|---|---|---|---|---|
| Common | 1,000 | Pig, Cow, Chicken, Sheep | 100 | Sentry, Dune, Coast, Wild, Tide |
| Uncommon | 2,500 | Zombie, Skeleton, Spider | 250 | Snout, Rib, Wayfinder, Raiser, Shaper, Host |
| Rare | 5,000 | Creeper, Slime, Magma Cube, Blaze | 600 | Eye, Spire, Flow, Bolt |
| Epic | 10,000 | Enderman, Wither Skeleton | 1,500 | Vex |
| Legendary | 20,000 | Iron Golem | 3,000 | Ward, Silence |

To change a price, edit `price:` in that tier. To add an item, add it to the tier's `items:` list. To move something to a different tier, move it between lists. Then `/coins reload`.

## Earning settings

In `config.yml` under `earning:` you can change the amount, the interval, turn the action bar message on/off, and turn on **AFK detection** (players who stand still stop earning). AFK detection is off by default.

## Data

Balances are saved in `plugins/Coins/data.yml` (on quit/stop, and every 5 minutes). Back this file up with your other server data.
