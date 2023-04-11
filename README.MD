# PayAll
This is a minecraft mod, with which you're able to pay everyone on the server. <br>
I made this out of boredom while watching a YouTube video from **DuperTrooper**, in which he paid everyone manually. <br>
He replied to someone in the comments, who asked why he doesn't use a mod to automate it, that he doesn't have a ***competent*** developer to do that, and because I thought it would be easy, I tried to make it!
### Currently supported and tested: 1.19.x, 1.18.x
### Currently supported but NOT tested: 
## Table of Contents

- [Usage](#usage)
  - [Default usage](#default-usage) 
  - [Custom usage](#custom-usage) 
  - [Dryrunning](#dryrunning) 
- [Building (so you don't get ratted)](#building)
  - [Cleanup](#cleaning-up)
## Usage
~~This mod uses chat messages, due to me be being STUPID and not figuring out how client-side commands can be made, even after 4 hours of testing.~~ <br>
**After some testing with hours of crying, commands have been implemented!**
### Default usage
``/payall <delay in seconds> <amount>`` <br>

**Examples:**
- ``/payall 2 6969``
- ``/payall 0.67 10000000``

### Custom usage
``/payall <delay in seconds> <amount> [custom command]...`` <br>
The default command used is ``/pay player amount`` (e.g. ``/pay SteinGaming 5000``) <br>
In case a server switched the properties or uses a completely different command, you can append the command desired to the [Default Usage](#default-usage). <br>
It provides these placeholders:
- ``!`` for the player name
- ``$`` for the amount

**Examples:**
- If the command is ``/deez PLAYER AMOUNT``:
  - Then ``/payall 2 6969 deez ! $``
- If the command is ``/nuts AMOUNT PLAYER``
  - Then ``/payall 0.67 10000000 nuts $ !``

### Dryrunning
``/payalldry ...`` <br>
If you have a problem, you want to list the players found or you just want to see the command that will be running, then you can use this to your advantage. <br>
Just use any of the previously mentioned usages and change ``/payall`` to ``/payalldry``. <br>
Nothing will be sent and this is just meant to help me find methods for identifying players and other kinds of debugging.

## Building
Minecraft mods have a tendency to be ratted, which is why I have the code public, with which everyone can check the code for any rats and build it for themselves. <br>
Just having the src public isn't enough, ~~so I'm not making any own releases for them.~~ <br>

**!!!! Releases MIGHT be coming, but this is still urgently recommended !!!!**

1. Download the project. You can:
    - Get it as an archive [here](https://github.com/SteinGaming/PayAll/archive/refs/heads/main.zip) and extract it
    - Use the ``git`` util to [clone it](https://github.com/git-guides/git-clone)
2. Open a terminal in the extracted/cloned directory (some can just right-click on empty space and click "Open Terminal") and execute the following command:
    - ```./gradlew.bat shadowJar```
3. The final .jar mod file can be found in `output/`
4. If you are done, [clean up afterward](#Cleaning-up)

### Cleaning up
Only deleting the directory in which you built the project is only a part of it, due to gradle downloading many dependencies. <br>
If you aren't a developer, then go to your home folder (on windows ``C:\Users\YOUR USER\``) and delete the ``.gradle`` folder. <br>
