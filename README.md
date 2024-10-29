# What is this fork?

This is my version of EarthComputer's clientcommands Minecraft client-side mod. I mainly focus on the fun aspects and mechanics of the GhostBlock.java file, possibly updating other things. This is mainly for me to become Frozone/Ice king by placing blocks at my feet instantly, water and bubble columns using hotkeys, and general movement stuff using ghostblocks. Nothing too technical, just messing around for added speed and movement in Minecraft. You can check out what this does [here](https://medal.tv/games/minecraft/clips/iVqGta8kmEpBq1BMq?invite=cr-MSxnTTIsNDUyMTcwOCw), keep in mind this is works on vanilla Minecraft, in survival. With the exception of no-flying rules (which you can generally bypass if you just touch a real block every 5-10 seconds), this mod allows you to traverse in any direction and access in vanilla with ease.

# How to use in-game

I recommend using [hotkeys](https://modrinth.com/mod/commandkeys) for this mod, makes usage much smoother. 

Features added to clientcommands: 

Syntax is as follows:

### Surf

- */cghostblock surf (<diameter>) (<block>) (<ylevel>)*

Turns every block under your feet into a block of your choosing. Configured for mounts like boats so you can blue_ice boat anywhere in the world. Use the command followed by your preferred diameter (1 block works perfectly as every replacement is updated every tick so you cannot fall), then choose a block (for speed, blue ice is great and my primary usage of this function). I'll attach a video here later for demonstration, but essentially you become Frozone from the Incredibles.

### Circle

- */cghostblock circle (<diameter>) (<block>)*

Replaces every block on the surface of the world with a block of your choosing in a circle around the player with your choice of diameter, updated every tick. Blocks are valid for replacement if they touch an air block from the top. To avoid transparent blocks with no collision, there are a list of exceptions such as grass, flowers, mushrooms, torches ect as they sit on top of block that would otherwise be touching an air block. The block below the grass or other transparent noncollision blocks will be replaced. The primary function of this is to turn every block on the surface into something like blue ice, so you can boat on the surface of the world without breaching the no-flying rules. You could also just turn everything into slime blocks so you can bounce around and have extra jump height.

## Recommendations for hotkeys for command combinations


### Stops and starts surf:

![image](https://github.com/user-attachments/assets/4dbab53e-c21e-4106-9ca2-e6f4245f5dcb)



### Bubble column up and fall damage cancelling:

![image](https://github.com/user-attachments/assets/263f08b7-0b3f-49de-916f-13649e57bc58)
On key pressed, this hotkey will spawn a 3x3 block of water below you, which will actually negate all fall damage provided you exit the water correctly. I recommend trying this mechanic out in your own time. When pressed again, it will spawn a bubble column from you up until 50 blocks in the air.



# clientcommands
Adds several useful client-side commands to Minecraft

## Social
Discord: https://discord.gg/Jg7Bun7
Patreon: https://www.patreon.com/earthcomputer

## Installation
1. Download and run the [Fabric installer](https://fabricmc.net/use).
   - Click the "vanilla" button, leave the other settings as they are,
     and click "download installer".
   - Note: this step may vary if you aren't using the vanilla launcher
     or an old version of Minecraft.
1. Download [Fabric API](https://minecraft.curseforge.com/projects/fabric)
   and move it to the mods folder (`.minecraft/mods`).
1. Download clientcommands from the [releases page](https://github.com/Earthcomputer/clientcommands/releases) or from [Modrinth](https://modrinth.com/mod/client-commands)
   and move it to the mods folder (`.minecraft/mods`).

## Contributing
1. Clone the repository
   ```
   git clone https://github.com/Earthcomputer/clientcommands
   cd clientcommands
   ```
1. Generate the Minecraft source code
   ```
   ./gradlew genSources
   ```
   - Note: on Windows, use `gradlew` rather than `./gradlew`.
1. Import the project into your preferred IDE.
   1. If you use IntelliJ (the preferred option), you can simply import the project as a Gradle project.
   1. If you use Eclipse, you need to `./gradlew eclipse` before importing the project as an Eclipse project.
1. Edit the code
1. After testing in the IDE, build a JAR to test whether it works outside the IDE too
   ```
   ./gradlew build
   ```
   The mod JAR may be found in the `build/libs` directory
1. [Create a pull request](https://help.github.com/en/articles/creating-a-pull-request)
   so that your changes can be integrated into clientcommands
   - Note: for large contributions, create an issue before doing all that
     work, to ask whether your pull request is likely to be accepted
