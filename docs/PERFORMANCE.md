# TODO: create a video and delete this file

# Performance Optimization

There's some neat tricks used to improve the performance of the mod, here's an overview :D

### Minimum Tick Rate

The minimum timer interval of 1 second makes crappy programs 20x more performant since they aren't running every
tick.

### Pattern Caching

![map from string to lambda](media/pattern%20cache.png)

Be not afraid, regular expressions are only used when necessary.

![string equals used when possible](media/predicate%20builder.png)

---

Using the `EACH` keyword to count by type rather than by matcher also employs a cache.

```sfm
EVERY 20 TICKS DO
    INPUT 2 EACH *ingot* FROM a
    OUTPUT TO b
END
```

This program will internally enumerate the registry to create a separate tracker for each resource type.

![hashmap inspection of a map to lists with three keys](media/expansion%20cache.png)

### Object Pooling

```sfm
EVERY 20 TICKS DO
    INPUT FROM a
    OUTPUT TO b
END
```

When many inventories are involved, this can quickly result in a lot of objects being created when the program runs.

![625 barrels](media/many%20barrels.png)

My testing shows that object pooling provides a slight increase in performance, even if there's only tens of thousands
of objects involved.

### Testing

I created a custom barrel block used only for testing. Running all the game tests for the mod creates 2,866 barrel
blocks.
Many of those barrels are so full of items that when I clear or restart the tests it causes 27,310 stacks to be dropped
on the ground.

By creating a custom barrel that doesn't drop the inventory contents, the friction of doing more tests is reduced!

![tests](media/tests.png)

### User Empowerment

```sfm
NAME "first"
EVERY 20 TICKS DO
    INPUT FROM a
    OUTPUT TO b
END
```

```sfm
NAME "second"
EVERY 20 TICKS DO
    INPUT stone, iron_ingot FROM a
    OUTPUT TO b
END
```

Which program is more efficient? idk. Use the performance graph and compare.

![in-game performance gui](media/performance%20first.png)
![in-game performance gui](media/performance%20second.png)

Cool. Looks like the first one is twice as fast. Maybe you need to filter items though? Maybe the outcome is different
if depending on the inventories?

Rather than trying to prescribe a best approach based on how I know the mod works, it's better to directly give the
players the tools needed to perform experiments to find out what works best in their scenario.
