Currently, the AST objects are used to execute the program itself

```sfm
every 20 ticks do
    input from a
    output to b
end
```

Let us assume that we have the following layout

```
 a 
bMb
 b
```

a: {(1,0,0)}
b: {(0,0,1), (2,0,1), (1,0,2)}
M: manager block at (1,0,1)

this doesn't have breakpoints or sleep across ticks. If we had a virtual machine with an instruction pointer, then we could have commands that use registers so the program would become

Required reading: [OutputStatement.java](../../platform/minecraft/src/main/java/ca/teamdman/sfml/ast/OutputStatement.java) teaches you how SFM currently works.


```lua # for syntax highlighting, not because this is lua lol
-- each tick, the manager has a vm/context initiated with instruction pointer at `0`.
-- it runs the instructions sequentially
-- a `sfm:control_flow/return` action will terminate the virtual machine
0: /sfm action execute sfm:control_flow/if sfm:bool/not sfm:math/eq sfm:math/modulo sfm:manager/tick 20 0 then sfm:control_flow/return
-- `if {bool} then {action}` has the `if` really be `sfm:control_flow/if` while `then` is a positional parameter that accepts the "then" string with any capitalization
-- we keep the "then" parameter for grokability rather than due to necessity

1: /sfm action execute sfm:variable/declare "inputSlots" sfm:data_type/array_deque sfm:limited_input_slot
-- sfm:variable/declare {variable name} {data type}
-- note that {data type} is expanded using brigadier where `sfm:data_type/array_deque` requires an additional parameter for the generic parameter
-- if we make the additional parameter optional and default to Object, that would invite trouble with command arity being variable which we don't want to deal with
-- we currently have a kind of Polish Notation going on with this proposal
-- there is no null, the ArrayDeque is initialized and ready

2: /sfm action execute sfm:capability/gather_input_slots 1 0 0 "inputSlots"
-- this is a complex action that could be expanded into its own instruction sequence
-- if a user wanted to put a breakpoint on that, then they could view source (perhaps `/sfm action execute sfm:definition/show sfm:capability/gather_input_slots`) on it
-- how breakpoints are stored and how their targetting and conditions works is not yet specified
-- probably `/sfm action execute sfm:breakpoint/add {instruction target}` and `/sfm action execute sfm:breakpoint/condition/add {breakpoint target} {condition}`

3: /sfm action execute sfm:variable/declare "inputSlotCount" sfm:data_type/integer
-- 

n: /sfm action execute sfm:variable/declare "outputSlots" sfm:data_type/array_deque sfm:limited_output_slot

3: /sfm action execute sfm:capability/gather_output_slots
```

cancellation safety: a manager may terminate the execution of a program by refusing to execute the next instruction
this may be because a time or resource budget has been exhausted

if we allow multiple assignment / clobbering, then `declare` is probably the wrong name


```lua
/sfm action execute sfm:control_flow/if sfm:bool/not sfm:math/eq sfm:math/modulo sfm:manager/tick 20 0 then sfm:control_flow/return
/sfm action execute sfm:variable/store "inputSlots" sfm:data_type/array_deque sfm:limited_input_slot new
/sfm action execute sfm:capability/gather_input_slots 1 0 0 "inputSlots"

/sfm action execute sfm:variable/store "inputSlotCount" sfm:data_type/integer sfm:data_type/array_deque/size "inputSlots"
/sfm action execute sfm:control_flow/if sfm:math/eq "inputSlotCount" 0 then sfm:control_flow/return
```

could instead skip using an intermediary variable

```lua
/sfm action execute sfm:control_flow/if sfm:bool/not sfm:math/eq sfm:math/modulo sfm:manager/tick 20 0 then sfm:control_flow/return
/sfm action execute sfm:variable/store "inputSlots" sfm:data_type/array_deque sfm:limited_input_slot new
/sfm action execute sfm:capability/gather_input_slots 1 0 0 "inputSlots"
/sfm action execute sfm:control_flow/if sfm:math/eq sfm:data_type/array_deque/size "inputSlots" 0 then sfm:control_flow/return
/sfm action execute sfm:variable/store "outputSlots" sfm:data_type/array_deque sfm:limited_output_slot new
/sfm action execute sfm:capability/gather_output_slots 0 0 1 "outputSlots"
/sfm action execute sfm:capability/gather_output_slots 2 0 1 "outputSlots"
/sfm action execute sfm:capability/gather_output_slots 1 0 2 "outputSlots"
-- we can avoid introducing loops by having it destructure labels into an instruction for each position with that label
-- however, when it comes time to moveTo(inputSlot1, outputSlot1) it will have to be a dynamic loop that we can't unroll. Let's see what I come up with below
/sfm action execute sfm:control_flow/if sfm:math/eq sfm:data_type/array_deque/size "outputSlots" 0 then sfm:control_flow/return
/sfm action execute sfm:control_flow/label "moveInputSlot"
/sfm action execute sfm:variable/store "currentInputSlot" sfm:

```

- use iterators objects and `sfm:control_flow/goto "moveInputSlot"` to accomplish a loop?
can we add a `sfm:control_flow/for` that desugars to our goto?

these aren't command palette actions, so perhaps instruction is more appropriate

```lua
/sfm instruction execute sfm:control_flow/if sfm:bool/not sfm:math/eq sfm:math/modulo sfm:manager/tick 20 0 then sfm:control_flow/return end
/sfm instruction execute sfm:variable/store "inputSlots" sfm:data_type/array_deque sfm:limited_input_slot new
/sfm instruction execute sfm:capability/gather_input_slots 1 0 0 "inputSlots"
/sfm instruction execute sfm:control_flow/if sfm:math/eq sfm:data_type/array_deque/size "inputSlots" 0 then sfm:control_flow/return end
/sfm instruction execute sfm:variable/store "outputSlots" sfm:data_type/array_deque sfm:limited_output_slot new
/sfm instruction execute sfm:capability/gather_output_slots 0 0 1 "outputSlots"
/sfm instruction execute sfm:capability/gather_output_slots 2 0 1 "outputSlots"
/sfm instruction execute sfm:capability/gather_output_slots 1 0 2 "outputSlots"
-- we can avoid introducing loops by having it destructure labels into an instruction for each position with that label
-- however, when it comes time to moveTo(inputSlot1, outputSlot1) it will have to be a dynamic loop that we can't unroll. Let's see what I come up with below
/sfm instruction execute sfm:control_flow/if sfm:math/eq sfm:data_type/array_deque/size "outputSlots" 0 then sfm:control_flow/return end
/sfm instruction execute sfm:control_flow/for "inputSlot" in "inputSlots" do
    /sfm instruction execute sfm:control_flow/if sfm:data_type/limited_input_slot/is_done "inputSlot" then sfm:control_flow/continue end
    /sfm instruction execute sfm:variable/store "outputSlotIter" sfm:data_type/iterator sfm:data_type/limited_output_slot sfm:data_type/array_deque/iterator "outputSlots"
    /sfm instruction execute sfm:control_flow/while sfm:data_type/iterator/has_next "outputSlotIter" do
        /sfm instruction execute sfm:variable/store "outputSlot" sfm:data_type/limited_output_slot sfm:data_type/iterator/next "outputSlotIter"
        ...
/sfm instruction execute sfm:control_flow/end
/sfm instruction execute sfm:control_flow/end

```

I also, in the above, introduced trailing `end` to `sfm:control_flow/if` so that `sfm:control_flow/end` can be used for if,for,loop style constructs.
It acts as a marker for the ordinal of the instruction in the list that the body of the control flow should be associated with.
Rather than having labels and instruction ordinals we `sfm:control_flow/goto` with, it's better to keep that implicit.
We can have `sfm:control_flow/label "abc" sfm:control_flow/for ...` if we want to later so we can have labelled breaks and stuff.
We can have goto if we want as well, because why not lol.
Once we have budgets for resource usage like time and memory, then we can have fun

```lua
/sfm instruction execute sfm:control_flow/if sfm:bool/not sfm:math/eq sfm:math/modulo sfm:manager/tick 20 0 then sfm:control_flow/return end
/sfm instruction execute sfm:variable/store "inputSlots" sfm:array_deque sfm:limited_input_slot new
/sfm instruction execute sfm:capability/gather_input_slots 1 0 0 "inputSlots"
/sfm instruction execute sfm:control_flow/if sfm:math/eq sfm:array_deque/size "inputSlots" 0 then sfm:control_flow/return end
/sfm instruction execute sfm:variable/store "outputSlots" sfm:array_deque sfm:limited_output_slot new
/sfm instruction execute sfm:capability/gather_output_slots 0 0 1 "outputSlots"
/sfm instruction execute sfm:capability/gather_output_slots 2 0 1 "outputSlots"
/sfm instruction execute sfm:capability/gather_output_slots 1 0 2 "outputSlots"
-- we can avoid introducing loops by having it destructure labels into an instruction for each position with that label
-- however, when it comes time to moveTo(inputSlot1, outputSlot1) it will have to be a dynamic loop that we can't unroll. Let's see what I come up with below
/sfm instruction execute sfm:control_flow/if sfm:math/eq sfm:array_deque/size "outputSlots" 0 then sfm:control_flow/return end
/sfm instruction execute sfm:control_flow/for "inputSlot" in "inputSlots" do
    /sfm instruction execute sfm:control_flow/if sfm:limited_input_slot/is_done "inputSlot" then sfm:control_flow/continue end
    /sfm instruction execute sfm:variable/store "outputSlotIter" sfm:iterator sfm:limited_output_slot sfm:array_deque/iterator "outputSlots"
    /sfm instruction execute sfm:control_flow/while sfm:iterator/has_next "outputSlotIter" do
        /sfm instruction execute sfm:variable/store "outputSlot" sfm:limited_output_slot sfm:iterator/next "outputSlotIter"
        -- we don't need to specify sfm:data_type/ because the sfm:variable/store command has positional arguments for `{name} {data type} {value}` where {value} can be 
        -- an "s" expression (I think I'm using that term correctly) and `{data_type}` is a lookup into what will be a registry of data types we will have
        -- it is unclear if there will be a datatype-specific registry, or if it will be a registry of expressions in general
        -- otherwise the expressions that resolve to a bool in `sfm:control_flow/if` would be in a function registry, and having a single registry instead of
        -- speciating into registries per construct feels less elegant than if we can get away with fewer
        -- We can probably write a program that desugars java into this grammar to make it easier to write than what I'm doing by hand here
        /sfm instruction execute sfm:control_flow/if sfm:output_slot/is_done "outputSlot" then -- is it okay that the arity on `sfm:control_flow/if` is variable?
            /sfm instruction execute sfm:iterator/remove "outputSlotIter"
            -- it is unclear how our `LimitedOutputSlotObjectPool.release(outputSlot);` translates here...
            -- perhaps we need some sfm:object_pool/acquire and sfm:object_pool/release stuff lol
            -- we may be able to make the underlying object pool an implementation detail of the drop handler for sfm:iterator/remove
            /sfm instruction execute sfm:control_flow/continue
        /sfm instruction execute sfm:control_flow/end

        /sfm instruction execute sfm:move_to "inputSlot" "outputSlot"
        -- the money shot ^^^
        -- we can expand this later like how `sfm:capability/gather_output_slots` also deserves an expansion

        /sfm instruction execute sfm:control_flow/if sfm:limited_input_slot/is_done "inputSlot" then sfm:control_flow/break end
    /sfm instruction execute sfm:control_flow/end
    /sfm instruction execute sfm:control_flow/if sfm:collection/is_empty "outputSlots" then sfm:control_flow/break end
    -- might as well leverage inheritance for sfm:collection/is_empty vs sfm:array_deque/is_empty to both work
/sfm instruction execute sfm:control_flow/end
-- drop handler for remaining outputSlots runs here
```

The safety for stopping execution at any point means that the object pool implementation won't work because that would mean we may stop execution
before we reach the instruction for returning an object back to the pool
so that will have to be implemented at the platform layer where the VM will have drop handlers that will ensure the correctness of our bookkeeping

If we permit users to write the instructions by hand and give them tooling to desugar a more natural syntax into these instructions, then we have
to make sure we maintain our cancellation safety in the face of adversarial/naive/learner programs that may have "mistakes"
(logical errors, since syntax errors and stuff will cause a compilation error).

Currently, our hot-loop that I've represented here is written in native java. If we introduce an abstraction layer through this command system,
we would want to ensure it maintains desirable performance characteristics.

Interistingly, because this approach is detached from the ANTLR stuff, our OutputStatement.java and existing SFML handling can stay as-is, while we
experiment with alternate execution mechanisms.

SFML programs can be compiled into this instruction set.
This instruction set can do things that have no representation in SFML.

This instruction set is more powerful.

We can expose operations like sorting and indexing so that people who want to be able to say "input from chest, output to furnace[5]" will have a way forward

For this to remain approachable, we would want to think about how this instruction set now influences how the g4 grammar would evolve so that we can convert back from
this instruction set to SFML.

Perhaps this is where I will share with you.

Part of the design of SFML is that it can be spoken out loud. Awkward symbols like "left square bracket" suck to pronounce and don't look like natural language.
