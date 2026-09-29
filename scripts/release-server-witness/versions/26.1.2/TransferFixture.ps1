# Test-only 26.1.2 component-backed transfer. The seed has no disk, so the
# official control and projected candidate begin from an inactive world copy.
function Get-ReleaseServerTransferFixture2612 {
    return @{
        manager = '0 120 0'
        source = '-1 120 0'
        destination = '1 120 0'
        source_nbt = '{Items:[{Slot:0b,id:"minecraft:dirt",count:64}]}'
        disk_nbt = '{Items:[{Slot:0b,id:"sfm:disk",count:1,components:{"sfm:program":"EVERY 20 TICKS DO INPUT FROM a OUTPUT TO b END","sfm:labels":{labels:{a:[[I;-1,120,0]],b:[[I;1,120,0]]}},"sfm:errors":[],"sfm:warnings":[]}}]}'
        expected_program = '"EVERY 20 TICKS DO INPUT FROM a OUTPUT TO b END"'
        expected_label_a = '[I; -1, 120, 0]'
        expected_label_b = '[I; 1, 120, 0]'
        poll_timeout_seconds = 60
    }
}
