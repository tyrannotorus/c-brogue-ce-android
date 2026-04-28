Brogue CE (Android)
=========
This is a personal modern fork of Brogue CE built for Android.
You may play it. It is unsupported. Pull requests welcome.

### Changes:
- Mobile-friendly Title Screen (just tap to play).
- Brogue dungeon is scaled to 2x.
- In-game Brogue menus remain at 1x.
- Android Menu overlay, including a mouse toggle, and inventory screen for better readability.
- Swipe gestures for movement. Two-fingers to drag map.

### APK Releases:
- Get them here!
  https://github.com/tyrannotorus/c-brogue-ce-android/releases

### Screenshots:

<img width="2340" height="1080" alt="Screenshot_20260428_203516_Brogue CE Android" src="https://github.com/user-attachments/assets/d0604f40-c2ac-4687-8975-8fb0a19c0231" />
<img width="2340" height="1080" alt="Screenshot_20260428_202838_Brogue CE Android" src="https://github.com/user-attachments/assets/dd20d48d-201d-4022-b19f-062a5349c7bd" />
<img width="2340" height="1080" alt="Screenshot_20260428_202723_Brogue CE Android" src="https://github.com/user-attachments/assets/c6b89f44-46d7-4242-b9f6-4a92e5e77347" />
<img width="2340" height="1080" alt="Screenshot_20260428_202550_Brogue CE Android" src="https://github.com/user-attachments/assets/bf6ae647-1be6-470c-8172-5f5ec94860f9" />
<img width="2340" height="1080" alt="Screenshot_20260428_202531_Brogue CE Android" src="https://github.com/user-attachments/assets/5ac54dfb-925f-419c-9e4f-f9e99b61abb2" />
<img width="2340" height="1080" alt="Screenshot_20260428_202506_Brogue CE Android" src="https://github.com/user-attachments/assets/02d7cbad-9421-4754-b96c-03be73f282d4" />

Brogue CE
=========

> *Countless adventurers before you have descended this torch-lit staircase,
> seeking the promised riches below. As you reach the bottom and step into
> the wide cavern, the doors behind you seal with a powerful magic...*
>
> ***Welcome to the Dungeons of Doom!***

*Brogue* is a single-player strategy game set in the halls of a mysterious
and randomly-generated dungeon. The objective is simple enough -- retrieve the
fabled Amulet of Yendor from the 26th level -- but the dungeon is riddled with
danger. Horrifying creatures and devious, trap-ridden terrain await. Yet it is
also riddled with weapons, potions, and artifacts of forgotten power. Survival
demands strength and cunning in equal measure as you descend, making the most
of what the dungeon gives you. You will make sacrifices, narrow escapes,
and maybe even some friends along the way -- but will you be one of the
lucky few to return alive?

- [Downloads][releases]
- [Wiki](https://brogue.fandom.com/wiki/Brogue_Wiki)
- [Forum (Reddit)](https://www.reddit.com/r/brogueforum/)
- [Roguelikes Discord](https://discord.gg/9pmFGKx) (we have a #brogue channel)
- IRC: [##brogue on Libera Chat](https://kiwiirc.com/nextclient/irc.libera.chat/##brogue)
- [Original website](https://sites.google.com/site/broguegame/)
- [Android port](https://github.com/bilgincoskun/brogue-android-port/releases)


Playing
-------

If you downloaded a [release][releases], you can open the game as follows:

### Windows

Run `brogue.exe`.

### Mac

Run the included app.

As it's an unsigned program, you may have to convince macOS to let you run it.
You can do this by right-clicking the app and choosing *Open* the first time you
run it.

### Linux

Run the `./brogue` script in the same the folder as this file.

Make sure you have SDL2 and SDL2_image installed via your package manager. The
required packages are:

- Debian/Ubuntu: `libsdl2-2.0-0 libsdl2-image-2.0-0`
- Fedora: `SDL2 SDL2_image`
- Arch: `sdl2 sdl2_image`

You can also run `./make-link-for-desktop.sh` to generate a .desktop file to
place on your desktop or applications folder.

### More information

Graphical tiles are included; press 'G' to toggle them.

For some tips on playing the game, see the original website, linked above. Also
check out the wiki -- although keep in mind CE may differ from it.

If you downloaded the source code, you will need to build the game first. For
instructions, see `BUILD.md`.

Brogue supports some command-line options; run `brogue --help` for more info.
(On Windows, if you want to see output in the command prompt, use
`brogue-cmd.bat`.)


Contributing
------------

Please see our [contribution guide][contrib] for all the ways you can help make
Brogue better!

[contrib]: https://github.com/tmewett/BrogueCE/wiki/Contribution-guide


About
-----

### What is Community Edition?

Brogue was created by Brian Walker. This version, *Brogue: Community Edition*,
is a continuation of its development. It has several main goals:

- fix bugs and crashes
- add useful quality of life and non-gameplay features
- improve the gameplay and keep it exciting
- ease development and maintenance
- be a convenient base for forks and ports to new platforms

### How is CE different from the original Brogue?

Please refer to the changelog or release history for a complete list. There is
also a wiki page: [Changes from original][cfo].

[cfo]: https://github.com/tmewett/BrogueCE/wiki/Changes-from-original

### How is the project run, and how are changes decided?

The project is run with a "benevolent dictator" model, with myself (tmewett) in
charge. There are many other testers and regular contributors; see the
contribution guide to get involved!

We always try to make changes in the same spirit as the rest of the game. Anyone
can propose a change - all discussion occurs in our GitHub issue tracker or on
our development Discord (see contributing guide). We also make sure that things are
well play-tested.


[releases]: https://github.com/tmewett/BrogueCE/releases
