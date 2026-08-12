# Bosstag Language Server
## Context
I am working on a Minecraft server with a custom plugin called Monumenta.
Monumenta supports custom mob behaviors through a system called "bosstags".
Each tag is backed by plugin code that runs as part of a larger event loop per
tick. These are runnables with an instance per/reference to mobs the tag is on.

Bosstags are actually string NBT tags that are parsed at spawn-time. They follow
the format `tag[param1=value1,param2=value2]`. As of today, developers edit
these tags using the NBTEditor mod or inline in minecraft chat commands. This
is naturally unwieldy; NBTEditor lacks support for any form of autocompletion,
while chat commands are length limited and can easily be lost with errant in-game
inputs.

## Goals
To allow for Monumenta developers to modify bosstags locally, inside their editor of choice
- Without sacrificing fast iteration time, with the quick production of testable spawn books
- Without touching existing workflows that already use chat
- While staying in sync with their desired plugin version, even with uncommitted changes
- Quickly and safely, with autocompletion and linting

## Out of Scope
- Development without having a Minecraft instance open

## Architecture
The fundamental source of truth for the list of tags, parameters and their
possible values is the Monumenta plugin on the mobs shard (server). One design
question, then, is how to get that information from the plugin to the
developer's computer. Additionally, for the sake of fast iteration, developers
should be able to generate testable spawn books from their local environment,
which requires communication the other way.

Typically, one might write an API for this purpose. However, to prevent
non-developers from accessing unreleased mob data, some implementation effort
into authentication would be necessary. Additionally, there would possibly be
some complexity introduced in the Monumenta architecture in routing traffic to
specifically the mobs shard.

The alternative here is to write a Minecraft game mod. There already exists
established library code for mod-plugin communication leveraging in-game
connections. Mob developers are already connecting in-game to the mob shard to
test their changes in the development lifecycle. Provenance is already
established by the development server whitelist. The trade-off is the inability
to develop without an active game connection to the server. Mob developers
say that they are always connected to the server to test their changes;
the developers that don't are usually performing operations in bulk on mobs,
in which case they would be operating directly on the database through ssh,
not individually editing mobs. Therefore, the decision is to use a Minecraft
game mod for communication with the Monumenta plugin.
