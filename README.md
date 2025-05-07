# MMOCraft Project - UUID Management

This document provides important information regarding the management of UUIDs within the MMOCraft Java project, particularly concerning the use of the `AutoUUIDReplace.ps1` PowerShell script.

## `AutoUUIDReplace.ps1` Script

This PowerShell script is located in `src/main/java/io/github/x1f4r/mmocraft/utils/AutoUUIDReplace.ps1`. Its purpose is to scan through the Java source files of the project and replace placeholder UUIDs with newly generated, unique UUIDs.

### Why Use This Script?

In Java, especially for things like `AttributeModifier` UUIDs or other unique identifiers, it's crucial to use actual unique UUIDs. Hardcoding placeholder values can lead to conflicts and bugs. This script automates the process of initializing these UUIDs with proper values.

### How to Use

1.  **BACKUP YOUR PROJECT**: Before running any script that modifies your source code, always ensure you have a recent backup or that your project is under version control (e.g., Git) with all changes committed.
2.  **Open PowerShell**: Navigate to the **root directory** of your `mmocraft` project (e.g., `C:\Dev\mmocraft`).
3.  **Run the Script**: Execute the script by providing the path to it:
    ```powershell
    .\src\main\java\io\github\x1f4r\mmocraft\utils\AutoUUIDReplace.ps1
    ```
    * If your Java source files are not in the default `.\src\main\java` relative to your project root, you can specify the path using the `-ProjectJavaRoot` parameter:
        ```powershell
        .\src\main\java\io\github\x1f4r\mmocraft\utils\AutoUUIDReplace.ps1 -ProjectJavaRoot "path\to\your\java\sources"
        ```
4.  **Confirm**: The script will ask you to confirm that you have backed up your project before proceeding.
5.  **Review Changes**: After the script finishes, review the changes it made to your Java files and rebuild your project.

### IMPORTANT: UUID Placeholder Template

For the `AutoUUIDReplace.ps1` script to correctly identify and replace placeholder UUIDs, any **newly added UUIDs** in your Java code that you intend for this script to replace **MUST** follow this template:

`UUID.fromString("YOUR_UNIQUE_UUID_HERE_ANY_SUFFIX_YOU_WANT")`

**Explanation of the template:**

* The UUID string must begin **exactly** with the prefix: `YOUR_UNIQUE_UUID_HERE`
* You can add any descriptive suffix after this prefix (e.g., `_ENDER_HELMET_BONUS`, `_MY_NEW_ITEM_ID`). This suffix helps you identify what the UUID is for before it's replaced.
    * Example 1: `UUID.fromString("YOUR_UNIQUE_UUID_HERE_EH_H")`
    * Example 2: `UUID.fromString("YOUR_UNIQUE_UUID_HERE_SPECIAL_EFFECT_MODIFIER")`

The script specifically looks for UUID strings starting with `"YOUR_UNIQUE_UUID_HERE"`. If your placeholder does not match this prefix, the script will ignore it, and it will not be replaced with a unique UUID.

**Why this template is crucial:**

* **Automation**: It allows the script to reliably find only the UUIDs that are meant to be placeholders.
* **Safety**: It prevents the script from accidentally modifying existing, valid UUIDs in your codebase.

Always use this template when adding new static UUIDs that require initialization with a unique value.
