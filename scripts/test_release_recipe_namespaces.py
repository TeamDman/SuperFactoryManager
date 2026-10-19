"""Deterministic #621/#527 resource and release parity checks, without builds.

Run: python scripts/test_release_recipe_namespaces.py
Reproduce baseline: python scripts/test_release_recipe_namespaces.py --family sfm-4.34.0
The baseline invocation must fail; its tracked resources intentionally stay unchanged.
"""
import argparse
import json
import os
from pathlib import Path
import re
import unittest

VERSIONS = ['1.19.2','1.19.4','1.20','1.20.1','1.20.2','1.20.3','1.20.4','1.21.0','1.21.1','26.1.2']
BASE_NAMES = ['disk_reset','label_gun_reset','tough_cable_horizontal','tough_cable_vertical',
              'tough_fancy_cable_horizontal','tunnelled_cable_horizontal','tunnelled_cable_vertical',
              'tunnelled_fancy_cable_horizontal','tunnelled_fancy_cable_vertical',
              'tunnelled_manager_horizontal','tunnelled_manager_vertical','uncraft_tunnelled_manager']
EXTRA_NAMES = ['fancy_to_cable','tough_to_cable','tough_fancy_to_fancy']

def expected_names(version):
    return BASE_NAMES + (EXTRA_NAMES if version == '26.1.2' else [])

def transform_advancement(obj, version):
    """Only recipe references change; vanilla parent/trigger/item IDs are retained."""
    result = json.loads(json.dumps(obj))
    mapping = {'minecraft:'+name:'sfm:'+name for name in expected_names(version)}
    for criterion in result.get('criteria',{}).values():
        conditions = criterion.get('conditions',{})
        if conditions.get('recipe') in mapping:
            conditions['recipe'] = mapping[conditions['recipe']]
    if 'recipes' in result.get('rewards',{}):
        result['rewards']['recipes'] = [mapping.get(id,id) for id in result['rewards']['recipes']]
    return result

def project_files(root):
    for folder, dirs, files in os.walk(root):
        if Path(folder) == root:
            dirs[:] = [d for d in dirs if d not in ['build','run','.gradle']]
        for name in files:
            yield Path(folder)/name

class RecipeNamespaceTests(unittest.TestCase):
    root = Path(__file__).resolve().parents[1]
    family = 'sfm-4.35.0'

    def project(self, version, family=None):
        return self.root/'platform/minecraft/projections'/(family or self.family)/('mc-'+version)

    def test_issue_621_no_stale_minecraft_owned_recipes(self):
        for version in VERSIONS:
            with self.subTest(version=version):
                project = self.project(version)
                directory = 'recipe' if version in ['1.21.0','1.21.1','26.1.2'] else 'recipes'
                base = project/'src/generated/resources/data'
                stale = list((base/'minecraft'/directory).glob('*.json'))
                self.assertEqual([],stale,'SFM-owned recipe IDs incorrectly use minecraft:')
                for name in expected_names(version):
                    recipe = json.loads((base/'sfm'/directory/(name+'.json')).read_text())
                    if name.endswith('_reset'):
                        self.assertEqual('sfm:'+name,recipe['type'])
                    else:
                        self.assertTrue(recipe['type'].startswith('minecraft:'))

    def test_issue_527_advancement_references(self):
        for version in VERSIONS:
            with self.subTest(version=version):
                directory = 'advancement' if version in ['1.21.0','1.21.1','26.1.2'] else 'advancements'
                category = 'sfm' if version == '1.19.2' else 'misc'
                data = self.project(version)/'src/generated/resources/data'
                for name in expected_names(version):
                    if name.endswith('_reset'):
                        continue
                    suffix = directory+'/recipes/'+category+'/'+name+'.json'
                    self.assertFalse((data/'minecraft'/suffix).exists(),suffix)
                    obj = json.loads((data/'sfm'/suffix).read_text())
                    self.assertEqual('minecraft:recipes/root',obj['parent'])
                    self.assertEqual(['sfm:'+name],obj['rewards']['recipes'])
                    criterion = obj['criteria']['has_the_recipe']
                    self.assertEqual('minecraft:recipe_unlocked',criterion['trigger'])
                    self.assertEqual('sfm:'+name,criterion['conditions']['recipe'])

    def test_new_family_selects_only_fix_and_version(self):
        catalog = json.loads((self.root/'platform/minecraft/projections.json').read_text())
        for version in VERSIONS:
            with self.subTest(version=version):
                old = catalog['sfm-4.34.0/mc-'+version]
                new = catalog['sfm-4.35.0/mc-'+version]
                self.assertEqual(old['minecraft_version'],new['minecraft_version'])
                self.assertEqual('release',new['environment'])
                self.assertEqual(old['features']+['recipe_namespace','release_version_4_35_0'],new['features'])
                properties = (self.project(version,'sfm-4.35.0')/'gradle.properties').read_text()
                self.assertRegex(properties,r'(?m)^mod_version=4\.35\.0$')

    def test_issue_621_datagen_changes_only_explicit_recipe_ids(self):
        relative='src/datagen/java/ca/teamdman/sfm/datagen/SFMRecipesDatagen.java'
        for version in VERSIONS:
            with self.subTest(version=version):
                old=(self.project(version,'sfm-4.34.0')/relative).read_text()
                expected=re.sub(r'\.save\(writer, "([a-z_]+)"\);',r'.save(writer, "sfm:\1");',old)
                pattern=r'(BuiltInRegistries\.RECIPE_SERIALIZER\.getKey\(SFMRecipeSerializers\.(DISK_RESET|LABEL_GUN_RESET)\.get\(\)\)\.getPath\(\)|SFMRecipeSerializers\.(DISK_RESET|LABEL_GUN_RESET)\.getPath\(\))'
                expected=re.sub(pattern,lambda m:'"sfm:'+('disk_reset' if 'DISK_RESET' in m[0] else 'label_gun_reset')+'"',expected)
                actual=(self.project(version,'sfm-4.35.0')/relative).read_text()
                self.assertEqual(expected,actual)
                test=(self.project(version,'sfm-4.35.0')/'src/test/java/ca/teamdman/sfm/test/Issue621RecipeNamespaceTests.java').read_text()
                self.assertNotIn('{%',actual)
                self.assertNotIn('{%',test)

    def test_whole_tree_parity_except_declared_changes(self):
        expected_changes = {
            'gradle.properties',
            'src/datagen/java/ca/teamdman/sfm/datagen/SFMRecipesDatagen.java',
            'src/main/resources/assets/sfm/template_programs/changelog.sfml',
        }
        test_path='src/test/java/ca/teamdman/sfm/test/Issue621RecipeNamespaceTests.java'
        for version in VERSIONS:
            with self.subTest(version=version):
                old = self.project(version,'sfm-4.34.0')
                new = self.project(version,'sfm-4.35.0')
                expected_paths = set()
                moved = 0
                for path in project_files(old):
                    relative = path.relative_to(old).as_posix()
                    if relative.split('/')[0] in ['build','run','.gradle']:
                        continue
                    output = relative
                    if re.match(r'src/generated/resources/data/minecraft/(recipes?/|advancements?/recipes/)',relative):
                        output=relative.replace('/data/minecraft/','/data/sfm/')
                        moved += 1
                    expected_paths.add(output)
                    actual = new/output
                    self.assertTrue(actual.is_file(),output)
                    if relative in expected_changes:
                        if relative == 'gradle.properties':
                            self.assertEqual(path.read_bytes().replace(b'mod_version=4.34.0',b'mod_version=4.35.0'),actual.read_bytes())
                        if relative.endswith('changelog.sfml'):
                            prefix='\n---- 4.35.0 ----\n-- Fix SFM recipe IDs and their advancement references using the minecraft namespace (#621, #527).\n-- Named crafting and reset recipes now use sfm: IDs. Recipe ingredients and results are unchanged.\n-- Datapacks and commands referencing the old minecraft: IDs must switch to sfm:.\n-- Move recipe overrides to data/sfm; previously unlocked recipes with changed IDs may need unlocking again.\n'
                            self.assertEqual(path.read_text().replace('NAME "Changelog"\n','NAME "Changelog"\n'+prefix,1),actual.read_text())
                        continue
                    if '/advancement' in relative and '/recipes/' in relative:
                        self.assertEqual(transform_advancement(json.loads(path.read_text()),version),json.loads(actual.read_text()),output)
                    else:
                        self.assertEqual(path.read_bytes(),actual.read_bytes(),output)
                self.assertEqual(28 if version == '26.1.2' else 22,moved)
                actual_paths={p.relative_to(new).as_posix() for p in project_files(new)}
                self.assertEqual(expected_paths | {test_path},actual_paths)

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root',type=Path,default=RecipeNamespaceTests.root)
    parser.add_argument('--family',default=RecipeNamespaceTests.family)
    args=parser.parse_args()
    RecipeNamespaceTests.root=args.repo_root.resolve()
    RecipeNamespaceTests.family=args.family
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(RecipeNamespaceTests)
    # Baseline reproduction needs no new family on disk.
    if args.family != 'sfm-4.35.0':
        suite=unittest.TestSuite(t for t in suite if t._testMethodName in ['test_issue_621_no_stale_minecraft_owned_recipes','test_issue_527_advancement_references'])
    raise SystemExit(not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful())
