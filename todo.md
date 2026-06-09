- audit command
    - ensure @MCVersionDependentBehaviour annotations are correct

- analyze diff between repos and repos2 for problematic changes

## 2026-02-01 Incident: SFMBlockCapabilities.java deleted during merge

### What happened
During the restructuring from `repos/` to `repos2/`, the file `SFMBlockCapabilities.java` was accidentally deleted in 1.20.3 during a merge from 1.20.2.

- **Problematic commit**: `ac9e1bc8c` ("Merge branch '1.20.2' into 1.20.3", 2026-02-01 13:36)
- **Last good commit**: `452215fb1` (the state matching `repos/SuperFactoryManager 1.20.3`)
- **File lost**: `D:\Repos\Minecraft\SFM\repos2\1.20.3\src\main\java\ca\teamdman\sfm\common\registry\SFMBlockCapabilities.java`
- **Should have been at**: `D:\Repos\Minecraft\SFM\repos2\1.20.3\platform\minecraft\src\main\java\ca\teamdman\sfm\common\registry\SFMBlockCapabilities.java`

### Root cause
`SFMBlockCapabilities.java` is a 1.20.3+ specific file (NeoForge capability rework) that doesn't exist in 1.20.2.
When merging 1.20.2 (restructured, no file) into 1.20.3 (old structure, has file), the conflict was resolved by deleting the file entirely instead of keeping it and moving it to the new `platform/minecraft/src/` path.

### Resolution
Restored the file from the last good commit:
```powershell
cd D:\Repos\Minecraft\SFM\repos2\1.20.3
git show 452215fb1:src/main/java/ca/teamdman/sfm/common/registry/SFMBlockCapabilities.java > platform\minecraft\src\main\java\ca\teamdman\sfm\common\registry\SFMBlockCapabilities.java
```

### Other files deleted in that merge (intentional vs accidental)

Files deleted from `src/main/java/` in commit `ac9e1bc8c`:

| File | Status | Notes |
|------|--------|-------|
| `SFMBlockCapabilities.java` | ❌ ACCIDENTAL | Restored manually |
| `SFMBlockModelWrappers.java` | ✅ OK | Exists in new location |
| `WaterTankBlockEntity.java` | ✅ OK | Exists in new location |
| `CableNetwork.java` | ✅ OK | Refactored to `block_network/` |
| `CableNetworkManager.java` | ✅ OK | Refactored to `block_network/` |
| `ICableBlock.java` | ✅ OK | Refactored to `block_network/` |
| `CompressedBlockPosSet.java` | ✅ OK | Exists in new location |
| `WaterNetworkManager.java` | ✅ OK | Uses generic `BlockNetwork` now |
| `Side.java` | ✅ OK | Exists in new location |
| `NotStored.java` | ⚪ INTENTIONAL | Annotation never used |
| `Stored.java` | ⚪ INTENTIONAL | Annotation never used |
| `WaterNetwork.java` | ⚪ INTENTIONAL | Replaced by generic `BlockNetwork<>` |

### Post-migration audit plan

Once all versions are migrated to repos2, run a diff to find missing files:

```powershell
# For each version branch (1.20.3, 1.20.4, 1.21.0, 1.21.1):
# Compare repos/ vs repos2/ accounting for path transformation

$versions = @(
    @{ old = "D:\Repos\Minecraft\SFM\repos\SuperFactoryManager 1.20.3"; new = "D:\Repos\Minecraft\SFM\repos2\1.20.3" },
    @{ old = "D:\Repos\Minecraft\SFM\repos\SuperFactoryManager 1.20.4"; new = "D:\Repos\Minecraft\SFM\repos2\1.20.4" },
    @{ old = "D:\Repos\Minecraft\SFM\repos\SuperFactoryManager 1.21.0"; new = "D:\Repos\Minecraft\SFM\repos2\1.21.0" },
    @{ old = "D:\Repos\Minecraft\SFM\repos\SuperFactoryManager 1.21.1"; new = "D:\Repos\Minecraft\SFM\repos2\1.21.1" }
)

foreach ($v in $versions) {
    Write-Host "=== Checking $($v.new) ===" -ForegroundColor Cyan
    
    # Get Java files from old repo under src/
    $oldFiles = Get-ChildItem -Path "$($v.old)\src" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName.Replace("$($v.old)\src\", "") }
    
    # Get Java files from new repo under platform/minecraft/src/
    $newFiles = Get-ChildItem -Path "$($v.new)\platform\minecraft\src" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName.Replace("$($v.new)\platform\minecraft\src\", "") }
    
    # Find files in old but not in new
    $missing = $oldFiles | Where-Object { $_ -notin $newFiles }
    
    if ($missing) {
        Write-Host "Missing files:" -ForegroundColor Red
        $missing | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host "All files accounted for" -ForegroundColor Green
    }
}
```

Known intentional deletions to ignore:
- `**/util/NotStored.java`
- `**/util/Stored.java`
- `**/watertanknetwork/WaterNetwork.java`
- `**/gametest/tests/ai/*.java`
