import unittest
from prepare_class_evidence import classify,normalized,split_views

class EvidenceTests(unittest.TestCase):
    def test_cache_requires_exact_tool_identity(self):
        import tempfile,json,hashlib
        from pathlib import Path
        import prepare_class_evidence as tool
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);jar=root/'a.jar';javap=root/'javap.exe';cache=root/'cached.json'
            jar.write_bytes(b'jar');javap.write_bytes(b'pinned tool')
            cache.write_text(json.dumps(dict(jar_sha256=hashlib.sha256(b'jar').hexdigest(),tool_sha256='wrong',classes={})))
            previous=tool.JAVAP;tool.JAVAP=javap
            try:
                with self.assertRaisesRegex(AssertionError,'pinned javap identity'):
                    tool.disassemble(jar,[],cache)
            finally:tool.JAVAP=previous
    def test_added_removed_content_metadata_and_unrepresented(self):
        self.assertEqual(classify(b'',b'x','','new','','',None,1,False,True),'added')
        self.assertEqual(classify(b'x',b'','old','','','',1,None,True,False),'deleted')
        self.assertEqual(classify(b'x',b'y','old','new','','',1,1),'representation')
        self.assertEqual(classify(b'x',b'x','same','same','','',1,2),'zip-metadata')
        self.assertEqual(classify(b'x',b'x','same','same','','',1,1),'unchanged')
        self.assertEqual(classify(b'x',b'y','same','same','a','b',1,1),'debug-view')
        self.assertEqual(classify(b'x',b'y','same','same','a','a',1,1),'unrepresented-bytes')
    def test_pool_indices_do_not_erase_string_literals_or_bootstrap_indices(self):
        self.assertEqual(normalized('  0: ldc #12 // String #123\n'),'  0: ldc #CP // String #123\n')
        self.assertIn('InvokeDynamic #0:',normalized('  4: invokedynamic #120, 0 // InvokeDynamic #0:call\n'))
    def test_debug_tables_do_not_consume_next_method(self):
        text='class A {\n  void a();\n    Code:\n       0: return\n    LineNumberTable:\n      line 10: 0\n\n  void b();\n    Code:\n       0: return\n}\n'
        main,debug=split_views(text)
        self.assertIn('void b();',main);self.assertNotIn('line 10:',main)
        self.assertIn('line 10:',debug);self.assertNotIn('void b();',debug)

if __name__=='__main__':unittest.main()
