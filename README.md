# Party Mod

> A party system mod for Hytale that allows players to group up and play together and see each other on the compass.

## Planned Features:
- Visible Party Members on the HUD with HP and Distance
- 3D Icons above Party Members' Heads
- Always visible names
- Party Chat
- All above configurable to your own liking

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### Core

- Create, join, leave & disband parties
- Browse available parties in the party list
- Invite players directly from the player list

### Access Control

| Type | Description |
|------|-------------|
| Open | Anyone can join freely |
| Password | Requires a password to join |
| Invite-only | Players must be invited by a party member |
| Locked | No new members can join |

### Roles & Permissions

```
Leader (5)
   └── Admin (4)
          └── Moderator (3)
                 └── Member (2)
                        └── Guest (1)
```

Higher ranks can promote, demote & kick lower ranks.

### Party Compass

See party members on the compass with distance in blocks, marked with a crown icon.

---

## Installation

1. Download the latest release
2. Place the `.jar` file in your server's `mods` folder, which is within ``universe/worlds/``
3. Restart the server

---

## Usage

```
/party create <name>     Create a new party
/party join <name>       Join an existing party
/party leave             Leave your current party
/party invite <player>   Invite a player to your party
/party kick <player>     Kick a player from your party
/party list              List all available parties
```

---

## Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.

---

## License

This project is licensed under the MIT License.
