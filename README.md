# Phonon

A custom audio system designed to orchestrate an immersive soundscape in your game.

Phonon is currently in alpha. Every Minecraft release receives its own NeoForge
artifact; the Minecraft version in the filename must match the game exactly.

## Features

- **Speaker Blocks** - Place in-world speakers that play custom audio
- **Real-time Sync** - All players hear the same audio at the same time
- **Universal URL Support** - YouTube, Bilibili, or any URL via yt-dlp integration
- **Spatial Audio** - Sound emanates from speaker locations
- **Streaming Playback** - Playback can begin before a track finishes downloading
- **Bounded Cache** - Downloaded OGG files are cached with configurable LRU-style eviction

## Requirements

Client and server:

- Java and NeoForge versions declared by the selected Minecraft artifact

Server-side (for audio downloading):

- [FFmpeg](https://ffmpeg.org/)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (optional, for URL support)

Direct OGG URLs work without yt-dlp. Other media sites require yt-dlp and its
FFmpeg integration.

## Commands

| Command | Description |
|---------|-------------|
| `/phonon add <name> <url>` | Download and register audio |
| `/phonon list` | View registered audio |
| `/phonon remove <name>` | Remove audio |
| `/phonon reload` | Reload configuration |

## Usage

1. Place a Speaker block
2. Right-click to open the control GUI
3. Select a track and press play

All players near the speaker will hear synchronized playback.

## Releases

Release tags use `v<mod-version>+mc<minecraft-version>`, for example
`v0.3.0-alpha.1+mc1.21.1`. Untested ports remain alpha, manually smoke-tested
builds may become beta, and only soaked beta builds become stable. See
[RELEASING.md](RELEASING.md) for the branch model, promotion rules, and test
checklist.
