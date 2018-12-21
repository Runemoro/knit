# Knit
IntelliJ plugin that adds support for editing Enigma mappings.

**Note:** The latest release on the JetBrains Plugin Repository may be outdated as it can take up to two days for new releases to be approved. To install the latest release, go to the [releases tab](https://github.com/DimensionalDevelopment/knit/releases) and download the most recent version. Then, open the Settings | Plugins menu in IntelliJ, click the cog icon, click "Install Plugin from Disk", select the downloaded zip, and restart IntelliJ.

## Instructions

### Setting up Minecraft sources

 1. Set up a gradle project with [this build.gradle](https://gist.github.com/Runemoro/f8ea27bece4806d8169c47ff8dcdf69a).</li>
 2. Export source code from enigma to `src/main/java`</li>
 3. Open the project in IntelliJ with the plugin installed</li>
 4. Move all classes in the default package to a new package named `nopackage` (can't be named something else)</li>
 5. Do a regex find-replace (Ctrl-Shift-R, check "Regex") and replace `\n\nimport` with `\n\nimport nopackage.*;\nimport`</li>

### Remapping

 1. Click "Refactor | Enable Remapping" and select the Enigma "mappings" folder</li>
 2. Rename classes, fields, methods, and parameters through Shift-F6</li>
 3. Save every once in a while using "Refactor | Save Mappings"</li>

**Note 1:** You may want to disable IntelliJ's "search and strings and comments" feature since it can lead to false matches when renaming obfuscated names. To do this, you can press Shift-F6 twice when renaming the first name and unchecking all boxes. It will stay disabled for the current project until you manually re-enable it.

**Note 2:** Undo is not yet supported. If you want to change a name after renaming it, just rename it again. If you accidentally undo, just redo and rename the name to the old name.
