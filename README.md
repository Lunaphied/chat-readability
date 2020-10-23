**ChatReadability** aims to simply provide better chat for Minecraft servers, without requiring complex setup, just drop it in your plugins and go. The aim is to be usable for servers focused on maintaining a vanillaish experience.

## Features

Improve chat readability, this is accomplished by the following:
* Players get unique username colors assigned upon each connection to the server, these are randomly selected on join and persist until they disconnect for now. The list of possible colors is configurable by the server owner using the config.yml
* Alternating lines of text get alternating light and dark values, this improves the readability of the actual chat, this can also be disabled.

## Future goals
That's about it for now, because I like to think too big, these are some potential plans, roughly ordered in usefulness/likeliness:
* Allow reloading config after server startup
* Add support for customizing the basic formatting somewhat like old [DisplayPrefixes](https://github.com/mineglow-network/DisplayPrefixes), but not terrible
* Simple nicknames by setting display name with a command
* Allow coloring of the alternating text to be selected, or even chosen from a rotating list
* Support defining specific users to have specific colors in the config, i.e. for a handful of admins/owners to have unique colors
* Handling locking names and randomization more nicely to avoid gaming the system or confusing changing colors too often
* Above but using permissions to allow it to happen while the server is running
* Bungeecord (probably velocity since it seems way nicer) support?

Currently, the colors are selected on join like this because I thought it was neat and simple: `"username".hashCode()*31+(LocalTime.now().getMinute() % listLength`. This was done in order to provide a simple way to avoid gaming the system (like a pure random approach), and I saw something similar somewhere and thought the approach seemed cute.

Also right now I'm sure some stuff isn't best practices, it's been a long time since I published a plugin, and I don't remember all of what the best approaches were. To be fair I'm not sure if I used my knowledge when I did have it looking back on my old plugins.