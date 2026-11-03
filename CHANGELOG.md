# Changelog

All notable changes to **LX Coop Fixes**.

## v1.3.0

Update to an already published mod. Earlier versions are on the Workshop page.

### Changed in this version

- Context menus now measure, fold and clamp against the viewport of the player who opened them, instead of the whole window (ISContextMenu has no viewport awareness at all).
- Long scrolling dialogs (ISModalRichText) grow and anchor inside their own half instead of jumping to the top of the window.
- Both fixes wrap the game's functions rather than replacing them, and fall back to the original behaviour on any error.
- Verified against a split-screen simulator: 18 checks covering single player, horizontal and vertical splits. Single-player behaviour is identical to vanilla.

### Included

- Vehicle crash while driving
- Player 2 could not aim
- Player 2+ had no reticle at all
- Stutter with the map or minimap open
- Error spam in the log
- Lost Lock Picking XP
- Menus and dialogs stayed on the other player's half
