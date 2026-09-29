# Test-only legacy-NBT transfer. The manager disk is inserted only after both
# copies have proven they begin with the same inactive, dirt64 seed world.
function Get-ReleaseServerTransferFixture1202 {
    return @{
        manager = '0 120 0'
        source = '-1 120 0'
        destination = '1 120 0'
        source_nbt = '{Items:[{Slot:0b,id:"minecraft:dirt",Count:64b}]}'
        # LabelPositionHolder reads the historical long-list format. These are
        # BlockPos.asLong(-1,120,0) and BlockPos.asLong(1,120,0).
        disk_nbt = '{Items:[{Slot:0b,id:"sfm:disk",Count:1b,tag:{"sfm:program":"EVERY 20 TICKS DO INPUT FROM a OUTPUT TO b END","sfm:labels":{a:[-274877906824L],b:[274877907064L]},"sfm:errors":[],"sfm:warnings":[]}}]}'
        expected_program = '"EVERY 20 TICKS DO INPUT FROM a OUTPUT TO b END"'
        expected_label_a = '-274877906824L'
        expected_label_b = '274877907064L'
        poll_timeout_seconds = 60
    }
}
