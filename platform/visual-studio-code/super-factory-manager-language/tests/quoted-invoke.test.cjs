// Run with the extension's installed dependencies: node --test tests/quoted-invoke.test.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { CharStreams, CommonTokenStream } = require('antlr4ts');

// Exercise the checked-in parser consumed by editor diagnostics, without emitting build files.
const generatedRoot = path.resolve(__dirname, '../src/generated') + path.sep;
const previousLoader = require.extensions['.ts'];
require.extensions['.ts'] = (loadedModule, filename) => {
    if (!filename.startsWith(generatedRoot)) {
        if (previousLoader) return previousLoader(loadedModule, filename);
        throw new Error(`Unexpected TypeScript module: ${filename}`);
    }
    const compiled = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
        fileName: filename,
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    });
    loadedModule._compile(compiled.outputText, filename);
};
const { SFMLLexer } = require('../src/generated/SFMLLexer.ts');
const { SFMLParser } = require('../src/generated/SFMLParser.ts');
if (previousLoader) require.extensions['.ts'] = previousLoader;
else delete require.extensions['.ts'];

function syntaxErrors(source) {
    const errors = [];
    const listener = {
        syntaxError(_recognizer, _symbol, line, column, message) {
            errors.push(`${line}:${column} ${message}`);
        },
    };
    const lexer = new SFMLLexer(CharStreams.fromString(source));
    lexer.removeErrorListeners();
    lexer.addErrorListener(listener);
    const parser = new SFMLParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    parser.program();
    return errors;
}

function program(expression) {
    return `CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n`
        + `LET arguments BE JSON "{}"\nLET result BE ${expression}\nEND`;
}

test('manual terminal mount example parses in editor diagnostics', () => {
    const source = String.raw`CLIENT BTW
EVERY FRAME FOR displays AS display DO
    LET terminalBinding BE JSON "{\"x\":12,\"y\":64,\"z\":8,\"channel\":\"sfm:terminal_demo\",\"input\":true}"
    LET terminalStatus BE INVOKE "sfm:terminal/display" WITH terminalBinding
    LET inputStatus BE INVOKE "sfm:terminal/input/status" WITH terminalBinding
    RENDER IMAGE "minecraft:textures/block/red_concrete.png" TO display
END`;
    assert.deepEqual(syntaxErrors(source), []);
});

for (const prefix of ['', 'STRING OF ']) {
    test(`${prefix}INVOKE retains an unquoted static action ID`, () => {
        assert.deepEqual(syntaxErrors(program(`${prefix}INVOKE sfm:terminal/display WITH arguments`)), []);
    });
    test(`${prefix}INVOKE accepts a quoted action ID containing the input keyword`, () => {
        assert.deepEqual(syntaxErrors(program(`${prefix}INVOKE "sfm:terminal/input/status" WITH arguments`)), []);
    });
    test(`${prefix}INVOKE accepts punctuation inside a quoted static action ID`, () => {
        assert.deepEqual(syntaxErrors(program(`${prefix}INVOKE "sfm:terminal-input/status" WITH arguments`)), []);
    });
    test(`${prefix}INVOKE rejects a computed action ID`, () => {
        assert.notDeepEqual(syntaxErrors(program(`${prefix}INVOKE actionId WITH arguments`)), []);
    });
}
