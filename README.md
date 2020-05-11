# Refined Storage

**Refined Storage is a mass storage mod for Minecraft that offers the player a network-based storage system, allowing them to store items and fluids on a massively expandable device network.**

Items and fluids can be stored in one of the many storage capabilities that the mod offers. Any storage devices connected to the same network are accessible to the player through one simple Grid interface, allowing the player to access the inventories of many devices through a single unified GUI.

This mod not only adds storage solutions, but also devices that can be used to manipulate items and blocks in the world and from within the system, such as importers, exporters, constructors, destructors, and more! There are also devices in this mod that allow the player to setup auto-crafting, allowing the crafting of complex recipes in a few simple clicks.

# More Refined Storage

This fork mainly focuses on performance improvements and useful feature addition to make your life easier.
Currently in progress is a new autocrafting engine (V8 engine) which will be the fastest and smartest that the world has ever seen.

Builds can be found in the release section on github.

### New things in this version
- Improved shift click crafting code to run about 100 times faster. (On a private server, crafting coal blocks took 8 seconds per stack. Now it's instant) 
- Fixed player inventory not updating when a remainder is added to the players inventory
- Fixed exporter not filling up slots to max stack size
- Fixed Controller using energy even when turned off
- The pattern grid now automatically switches between processing and crafting when tranferring from JEI or inserting an existing pattern
- Backported the crafting engine V6 which is currently feature incomplete
- Backported the regulator upgrade, which keeps a specific amount of items in the connected inventory
