# Phonon

A custom audio system designed to orchestrate an immersive soundscape in your game.

## Features

- **Speaker Blocks** - Place in-world speakers that play custom audio
- **Real-time Sync** - All players hear the same audio at the same time
- **Universal URL Support** - YouTube, Bilibili, or any URL via yt-dlp integration
- **Spatial Audio** - Sound emanates from speaker locations

## Requirements

Server-side (for audio downloading):
- [FFmpeg](https://ffmpeg.org/)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (optional, for URL support)

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
