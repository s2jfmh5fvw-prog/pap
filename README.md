# yuuh92 HomesTeams

Homes and themed teams for the yuuh92 Peace and Purge SMP on Paper 1.21.11.

## Commands

- `/home` opens the Home GUI; use `/home set|teleport|delete <name>` for direct management.
- `/team` opens the Team GUI; teams can be `allgemein`, `peace`, or `purge`.
- `/team create <name> <type>`, `/team invite <player>`, `/team accept <team>`, `/team leave`, and `/team home [set]`.
- `/purge` displays the current configured Peace/Purge phase.

Players receive two home slots; `yuuh92.homes.vip1`, `vip2`, and `vip3` increase this to 3, 4, and 5. Costs and phase hours are configurable in `config.yml`. Data is saved in `plugins/yuuh92HomesTeams/data.yml`.

When Vault and an Economy provider (such as a yuuh92 Economy integration) are present, all costs use that provider. Otherwise the plugin uses its own persistent fallback balances.

## Build

Run `make` to build both server artifacts:

- `build/pap-plugin.jar` — Paper plugin
- `build/pap-datapack.zip` — datapack
