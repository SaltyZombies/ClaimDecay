# ClaimDecay

ClaimDecay is a Minecraft plugin for Paper/Spigot servers that automatically resets inactive claims to their default state based on a configurable decay timer. It integrates with LuckPerms for permission management and uses WorldEdit for claim backups and restoration.

## Features

- Configurable decay timer for claims
- Permission-based decay settings and exemptions
- Automatic claim backup and reset using WorldEdit
- Admin command to restore claims from backups

## Installation

1. Download the latest release from the releases page.
2. Place the `ClaimDecay.jar` file in your server's `plugins` folder.
3. Restart your server to generate the configuration files.
4. Configure the plugin as needed in the `config.yml` file.

## Commands

- `/restoreclaim` - Restore a claim from backup

## Permissions

- `decay.set.<days>` - Set the decay timer in days
- `decay.exempt` - Exempt from decay

## License

This project is licensed under the MIT License. See the LICENSE file for details.
