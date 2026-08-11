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
- Without sacrificing fast iteration time, with the quick production of testable books
- Without touching existing workflows that already use chat
- While staying in sync with their desired plugin version, even with uncommitted changes
- Quickly and safely, with autocompletion and linting

## Out of Scope

