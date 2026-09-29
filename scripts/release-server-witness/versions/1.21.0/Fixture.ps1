# Test-only Minecraft 1.21 component-backed form of the six selected save values.
# The shared runner remains responsible for isolated roots and the four-boot comparison.
function Get-ReleaseServerFixture1210 {
    $queries = [ordered]@{
        disk_program = 'data get block 0 120 0 Items[0].components."sfm:program"'
        derived_name = 'data get block 0 120 0 Items[0].components."minecraft:item_name"'
        labels       = 'data get block 0 120 0 Items[0].components."sfm:labels"'
        errors       = 'data get block 0 120 0 Items[0].components."sfm:errors"'
        warnings     = 'data get block 0 120 0 Items[0].components."sfm:warnings"'
        facade       = 'data get block 2 120 0 "sfm:facade"'
    }
    return @{
        queries = $queries
        disk_nbt = '{Items:[{Slot:0b,id:"sfm:disk",count:1,components:{"sfm:program":"NAME \"compat-probe\" EVERY 20 TICKS DO END","sfm:labels":{labels:{legacy:[[I;0,120,0]]}},"sfm:errors":[],"sfm:warnings":[]}}]}'
        facade_nbt = '{"sfm:facade":{block_state:{Name:"minecraft:stone"},texture_mode:"STRETCH",direction:"north"}}'
    }
}
