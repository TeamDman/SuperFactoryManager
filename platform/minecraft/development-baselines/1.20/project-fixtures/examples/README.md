# Examples

This directory contains some example scripts for getting familiar with the language that SFM uses.

## Basics

All inventories must be connected to the manager via inventory cables for them to be usable by the program.  
The manager itself acts as an inventory cable as well.

Use the label gun to apply or remove labels from blocks in the world.  
Hold shift while scrolling with the label gun in hand to cycle through the loaded labels.

I recommend writing your program, then pulling the labels from the program instead of manually retyping all the labels you used.


An example video of loading scripts and labels into a manager:

https://user-images.githubusercontent.com/9356891/171307447-5895b809-9685-4e1c-a5e5-f102b6620b68.mp4


## Details

Keywords are case-insensitive.

Super-nerds can read the grammar file [here](../platform/minecraft/src/main/antlr/sfml/SFML.g4)

### Triggers

- A program consists of an ordered list of triggers
- Each trigger has an ordered list of inputs
- Each trigger clears the input list after executing (so they don't affect other triggers)
- Each trigger contains a block, which is an ordered list of statements

Example:

```lua
EVERY 20 TICKS DO
  -- do stuff here
END
```

```lua
every 5 seconds do
  -- do stuff here
end
```

### Statements

There are currently 3 statements.

- INPUT
- OUTPUT
- IF

See example scripts for detailed usage
