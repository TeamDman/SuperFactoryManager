# Changelog

## [0.0.14]

### Changes on code

- **Markdown**
  - Support for markdown codeblock, you can use \```sfm\``` or \```sfml``` for your codes, no snippets

## [0.0.13]

### Changes on code

- **Snippets**
  - Changed to always activate intellisense even if it has no snippet to only activate (forced) when it has at least 1 snippet

- **Syntax**
  - We still have that bad regex setup, but only for tooltip

### Added

- **Tooltips**
  - On key words, like input, output, ... it will show a small resume of what it does and some examples, with colors!
  Thanks to the clangd (<https://github.com/clangd/vscode-clangd/tree/master>) extension for the idea!

- **COLORS!!!**
  - And fully customizable (<https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide#theming>), now, with a lot of colors, for free.
  Some still relies on that regex setup, but only a few keyword and until semantic Tokens are on

## [0.0.12]

### Changes on code

- **Unification of snippets**
  - Basically, only one big file with all snippets and easier to mantain

- **Changes**
  - It will only trigger snippets when needed and will show IntelliSense (suggestions) when not

## [0.0.10 - 0.0.11]

### Changes on code

- **Error checking / Warning checking**
  - Solved issue that it triggered on non-sfml files
  - Update parser to include the lastest .g4

- **SFMLTreeDataProvider**
  - Simplify the class

- **SFML.g4**
  - Update to lastest

## [0.0.9]

### Added

- **Settings:** Added settings for activity bar, snippets, error chechink and warning

- **Activity bar:**
  - Now you can share with others your repo from GitHub with examples!
  Just go to settings and add a new URL on external URL. Example: `'https://api.github.com/repos/TeamDman/SuperFactoryManager/contents/', 'https://github.com/TeamDman/SuperFactoryManager/tree/1.19.2/examples', 'C:\Some cool games\minecraft'`.
  ‎
  All of these works
  ‎
  - Added the option to change files and folder icons on the activity bar (not on vscode, blame them)
  ‎
  - Added the option to disable the activity bar
  ‎
- **Error Checking:** Now you do not have to copy and paste the code on your controller to check if you write it correctly, vscode will tell you if everything is right (i hope)
‎
It has an setting to disable it, just in case
‎
- **Warnings:** Now, it will check if your output has its corresponding input and viceversa.
It might have some errors when it enconters an IF, for example, if the input is inside the if and the output is outside.
It can be disable, just in case
‎
- **Snippets:** Now, with more customization! You can leave them as normal or go to settings to add a prefix to activate snippets.
For example, if you want the snippets to activate only when the word starts with /
With a setting to disable them, just in case

## [0.0.8]

### Changes on code

- **Snippets:** every snippet will trigger instead of some random word if you do `every ` with a space

- **Examples:** Now, there will be a tab on the Activity bar with examples, both from in-game and github.
‎
Files will only download when you click on it (will open a new tab) and stored on the temp folder of your OS.
These files will be deleted when you close all vscode windows or extension is desactivated by changing folder with no `.sfm` or `.sfml`

### Known issues

- Folder on the Activity bar will not close unless you do multiple clicks.

## [0.0.6]

### Changes on code

- **Extension:** Now, every `.vsix` will be on its own folder, to avoid future mistakes 😉
‎
- **Typescript:** Added the scr folder with a blank example and the module needed for ts
‎
- **Icons changes:** Previous icons has some blank spaces, which made the icon on file smaller. Now, file icons will be larger in general
‎
- **.gitignore :** Added it for the carpet `/out`
‎
- **Tasks and lauch options:** There are 2 launch options:
  - `Debug Visually Extension`: For just colors
  - `Compile and Debug Extension`: For code debugging
    ‎
    Also added a task that automatically launch when using `Compile and Debug Extension` to compile the project
  
### Added

- **Folding:** Now, folding or collapsing from if and every should be down better

- **Keywords:**
  - `everY` or `eVERy` will be hightlighted. Also done for others keywords.
  - Added some missing boolean operants (`<`, `>`, `=`, `>=`, `<=`)

- Versions 0.0.6 and 0.0.7 are the same changelog

## [0.0.5]

### Added

- **Correct numbers coloring:**. Now, labels with numbers will not have a colored number.
‎
- **Test Folder:** Added 2 files: .sfm and .sfml
Those files have 2 examples, one using a Phytogenic insolator setup and another with random words to check if everything was done correctly
‎
- **File Icons:** `.sfm` and `.sfml` file extensions have now icons, using the disk from the mod.
‎
- **Extended Color Palette:** Expanded color options beyond the 3-color limit (in some themes).
Colors depends on how the theme using.
‎
- **New Snippets:** Added snippets including `basic`, `energy`, `if`, `ifelse`, `ifelseif`, `input`, and `output`.
‎
- **Extension Icon:** Updated the extension icon from the default to a custom design.

## [0.0.1 - 0.04]

- Initial release
