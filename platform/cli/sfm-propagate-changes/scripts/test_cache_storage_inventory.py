"""Windows-only, read-only inventory accounting witnesses on owned temp files."""

import os
from pathlib import Path
import runpy
import tempfile
import unittest


INVENTORY = runpy.run_path(str(Path(__file__).with_name("cache-storage-inventory.py")),
                           run_name="cache_inventory_test")["inventory"]


@unittest.skipUnless(os.name == "nt", "Win32 file allocation API")
class StorageInventoryTests(unittest.TestCase):
    def test_hardlink_names_share_one_allocation_but_copies_do_not(self):
        with tempfile.TemporaryDirectory() as temporary:
            first = Path(temporary, "first")
            second = Path(temporary, "second")
            first.mkdir()
            second.mkdir()
            source = first / "source.bin"
            source.write_bytes(b"a" * 3000)
            alone = INVENTORY([str(first)])[0]
            os.link(source, second / "alias.bin")
            shared = INVENTORY([str(first), str(second)])
            self.assertEqual(sum(root["logical_bytes"] for root in shared), 6000)
            self.assertEqual(sum(root["allocated_unique_bytes"] for root in shared),
                             alone["allocated_unique_bytes"])
            self.assertEqual(sum(root["hardlink_aliases"] for root in shared), 1)
            self.assertEqual(sum(root["multi_link_files"] for root in shared), 2)
            (second / "copy.bin").write_bytes(source.read_bytes())
            copied = INVENTORY([str(first), str(second)])
            self.assertEqual(sum(root["hardlink_aliases"] for root in copied), 1)
            self.assertEqual(sum(root["allocated_unique_bytes"] for root in copied),
                             alone["allocated_unique_bytes"] * 2)
            self.assertTrue(all(not root["errors"] for root in copied))


if __name__ == "__main__":
    unittest.main()
