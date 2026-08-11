import * as vscode from 'vscode';
import {CharStreams, CommonTokenStream} from 'antlr4ts';
import {ParseTree, ParseTreeListener, ParseTreeWalker, TerminalNode} from 'antlr4ts/tree';
import {Token} from 'antlr4ts/Token';
import {SFMLLexer} from '../generated/SFMLLexer';
import {SFMLParser} from '../generated/SFMLParser';

// Cache para árboles de análisis
const parseTreeCache = new Map<string, ParseTree>();

const TOOLTIP_DEFINITIONS: Record<number, { description: string; examples: string[] }> = {
    [SFMLLexer.IF]: {
        description: "**IF**\n\nConditional statement that executes a block if the expression evaluates to true",
        examples: [
            "if redstone > 5 then ... end",
            "if chest has lt 10 coal then ... end"
        ]
    },
    [SFMLLexer.ELSE]: {
        description: "**ELSE**\n\nOptional branch for IF statements when the condition is false",
        examples: [
            "if redstone > 5 then ... else ... end"
        ]
    },
    [SFMLLexer.OVERALL]: {
        description: "**OVERALL**\n\nChecks if the condition applies to the entire inventory collectively (same as leaving empty)",
        examples: [
            "if overall chest has > 1000 stone then ... end"
        ]
    },
    [SFMLLexer.SOME]: {
        description: "**SOME**\n\nChecks if some of the labels meets the conditions",
        examples: [
            "if some chest has < 64 coal then ... end"
        ]
    },
    [SFMLLexer.ONE]: {
        description: "**ONE**\n\nChecks if one label meets the conditions",
        examples: [
            "if one chest has < 64 coal then ... end"
        ]
    },
    [SFMLLexer.NOT]: {
        description: "**NOT**\n\nNegate the expression (true -> false and false -> true)",
        examples: [
            "if not (chest has lt 5 coal) then ... end"
        ]
    },
    [SFMLLexer.AND]: {
        description: "**AND**\n\nLogical conjunction operator (both conditions must be true)",
        examples: [
            "if chest has lt 5 coal and furnace has lt 5 iron_* then ... end"
        ]
    },
    [SFMLLexer.OR]: {
        description: "**OR**\n\nLogical disjunction operator (one condition or more can be true)",
        examples: [
            "if chest has lt 5 coal or chest has eq 5 charcoal then ... end"
        ]
    },
    [SFMLLexer.GT]: {
        description: "**> (GT)**\n\nGreater than comparison",
        examples: [
            "if redstone gt 5 then ... end",
            "if chest has gt 10 coal then ... end"
        ]
    },
    [SFMLLexer.GT_SYMBOL]: {
        description: "**> (GT)**\n\nGreater than comparison",
        examples: [
            "if redstone > 5 then ... end",
            "if chest has > 10 coal then ... end"
        ]
    },
    [SFMLLexer.LT]: {
        description: "**< (LT)**\n\nLess than comparison",
        examples: [
            "if redstone lt 15 then ... end",
            "if chest has lt 64 coal then ... end"
        ]
    },
    [SFMLLexer.LT_SYMBOL]: {
        description: "**< (LT)**\n\nLess than comparison",
        examples: [
            "if redstone < 15 then",
            "if chest has < 64 coal then"
        ]
    },
    [SFMLLexer.EQ]: {
        description: "**= (EQ)**\n\nEquality comparison",
        examples: [
            "if redstone eq 10 then",
            "if chest has eq 0 coal then"
        ]
    },
    [SFMLLexer.EQ_SYMBOL]: {
        description: "**= (EQ)**\n\nEquality comparison",
        examples: [
            "if redstone = 10 then",
            "if chest has = 0 coal then"
        ]
    },
    [SFMLLexer.LE]: {
        description: "**<= (LE)**\n\nLess than or equal comparison",
        examples: [
            "if redstone le 7 then",
            "if chest has le 32 coal then"
        ]
    },
    [SFMLLexer.LE_SYMBOL]: {
        description: "**<= (LE)**\n\nLess than or equal comparison",
        examples: [
            "if redstone <= 7 then",
            "if chest has <= 32 coal then"
        ]
    },
    [SFMLLexer.GE]: {
        description: "**>= (GE)**\n\nGreater than or equal comparison",
        examples: [
            "if redstone ge 12 then",
            "if chest has ge 64 coal then"
        ]
    },
    [SFMLLexer.GE_SYMBOL]: {
        description: "**>= (GE)**\n\nGreater than or equal comparison",
        examples: [
            "if redstone >= 12 then",
            "if chest has >= 64 coal then"
        ]
    },
    [SFMLLexer.INPUT]: {
        description: "**INPUT**\n\nExtracts contents from an inventory",
        examples: [
            "input from chest",
            "input fluid::, item::, gas:: from interface",
            "input fe:: from \"mek_cube™️\" top side"
        ]
    },
    [SFMLLexer.OUTPUT]: {
        description: "**OUTPUT**\n\nSends contents to an inventory",
        examples: [
            "output to chest",
            "output fluid::, item::, gas:: to interface",
            "output fe:: to \"mek_cube™️\" top side"
        ]
    },
    [SFMLLexer.SLOTS]: {
        description: "**SLOTS**\n\nSpecifies a particular inventory slots (not all slots are available)",
        examples: [
            "output fluid:: to furnace slots 1-3",
            "input from chest slots 5,9,13"
        ]
    },
    [SFMLLexer.RETAIN]: {
        description: "**RETAIN**\n\nKeeps a minimum quantity of items in the source inventory if used on input\n Send as maximum as the limit if used on output",
        examples: [
            "input retain 64 coal from chest",
            "output retain 4 coal to furnace"
        ]
    },
    [SFMLLexer.EACH]: {
        description: "**EACH**\n\nApplies the operation to every matching element",
        examples: [
            "input from each chest",
            "if each chest has > 0 then ... end"
        ]
    },
    [SFMLLexer.EXCEPT]: {
        description: "**EXCEPT**\n\nExcludes specific items, fluids, gas, energy from the operation",
        examples: [
            "input * except cobblestone, dirt from chest",
            "output fluid:: except fluid::lava to interface"
        ]
    },
    [SFMLLexer.FORGET]: {
        description: "**FORGET**\n\nClears the previous inputs, can be used to forget labels too",
        examples: [
            "forget",
            "forget chest"
        ]
    },
    [SFMLLexer.WITHOUT]: {
        description: "**WITHOUT**\n\nFilters items lacking the specified tags",
        examples: [
            "input without #minecraft:logs",
            "output without #c:my_super_dupper_tag"
        ]
    },
    [SFMLLexer.WITH]: {
        description: "**WITH**\n\nFilters items having the specified tags",
        examples: [
            "input with #minecraft:logs",
            "output with #c:my_super_dupper_tag"
        ]
    },
    [SFMLLexer.ROUND]: {
        description: "**ROUND ROBIN BY**\n\nDistribute items (only to one output at the time, slow) depending if its by block or label",
        examples: [
            "output to chest round robin by block",
            "input from interface1, interface2 round robin by label"
        ]
    },
    [SFMLLexer.ROBIN]: {
        description: "**ROUND ROBIN BY**\n\nDistribute items (only to one output at the time, slow) depending if its by block or label",
        examples: [
            "output to chest round robin by block",
            "input from interface1, interface2 round robin by label"
        ]
    },
    [SFMLLexer.BY]: {
        description: "**ROUND ROBIN BY**\n\nDistribute items (only to one output at the time, slow) depending if its by block or label",
        examples: [
            "output to chest round robin by block",
            "input from interface1, interface2 round robin by label"
        ]
    },
    [SFMLLexer.TOP]: {
        description: "**TOP**\n\nSpecifies the top side of a block",
        examples: [
            "input from machine top side",
            "output to furnace top slots 1-3"
        ]
    },
    [SFMLLexer.BOTTOM]: {
        description: "**BOTTOM**\n\nSpecifies the bottom side of a block",
        examples: [
            "input from machine bottom side",
            "output to furnace bottom slots 1-3"
        ]
    },
    [SFMLLexer.NORTH]: {
        description: "**NORTH**\n\nSpecifies the north side of a block",
        examples: [
            "input from machine north side",
            "output to furnace north slots 1-3"
        ]
    },
    [SFMLLexer.EAST]: {
        description: "**EAST**\n\nSpecifies the east side of a block",
        examples: [
            "input from machine east side",
            "output to furnace east slots 1-3"
        ]
    },
    [SFMLLexer.SOUTH]: {
        description: "**SOUTH**\n\nSpecifies the south side of a block",
        examples: [
            "input from machine south side",
            "output to furnace south slots 1-3"
        ]
    },
    [SFMLLexer.WEST]: {
        description: "**WEST**\n\nSpecifies the west side of a block",
        examples: [
            "input from machine west side",
            "output to furnace west slots 1-3"
        ]
    },
    [SFMLLexer.SIDE]: {
        description: "**SIDE**\n\nSpecifies a direction to do the operation or all directions",
        examples: [
            "input from machine top side",
            "output to furnace bottom slots 1-3",
            "input from interface each side"
        ]
    },
    [SFMLLexer.TICKS]: {
        description: "**TICKS**\n\nTime unit (1 tick = 0.05 seconds)",
        examples: [
            "every 5 ticks do ... end",
            "every 40 ticks do ... end"
        ]
    },
    [SFMLLexer.TICK]: {
        description: "**TICK**\n\nRepresent one tick, can only be used with energy (without configuration changes) ",
        examples: [
            "every tick do"
        ]
    },
    [SFMLLexer.SECOND]: {
        description: "**SECOND**\n\nTime unit (represents 20 ticks)",
        examples: [
            "every second do"
        ]
    },
    [SFMLLexer.SECONDS]: {
        description: "**SECONDS**\n\nTime unit",
        examples: [
            "every 2 seconds do",
            "every 50 seconds output *"
        ]
    },
    [SFMLLexer.REDSTONE]: {
        description: "**REDSTONE**\n\nReferences redstone power level on the manager block",
        examples: [
            "if redstone > 0 then",
            "every redstone pulse do"
        ]
    },
    [SFMLLexer.PULSE]: {
        description: "**PULSE**\n\nTriggers on redstone signal changes on the manager block",
        examples: [
            "every redstone pulse do"
        ]
    },
    [SFMLLexer.NAME]: {
        description: "**NAME**\n\nNames the current program (optional)",
        examples: [
            "name \"My super dupper laggy program\"",
            "name \"Redstone factory v3\""
        ]
    },
    [SFMLLexer.EVERY]: {
        description: "**EVERY**\n\nCreates a timed trigger",
        examples: [
            "every 5 ticks do ... end",
            "every 10 ticks do ... end",
            "every redstone pulse do ... end",
            "every second do ... end"
        ]
    }
};

class TooltipFinder implements ParseTreeListener {
    public result: TerminalNode | null = null;
    
    constructor(private position: vscode.Position) {}

    visitTerminal(node: TerminalNode) {
        const token = node.symbol;
        if (this.isPositionInToken(token) && TOOLTIP_DEFINITIONS[token.type]) {
            this.result = node;
        }
    }

    private isPositionInToken(token: Token): boolean {
        const tokenLine = token.line - 1;
        const tokenStartCol = token.charPositionInLine;
        const tokenEndCol = tokenStartCol + (token.stopIndex - token.startIndex + 1);

        return (
            tokenLine === this.position.line &&
            this.position.character >= tokenStartCol &&
            this.position.character <= tokenEndCol
        );
    }
}

export function activateTooltip(context: vscode.ExtensionContext) {
    context.subscriptions.push(
        vscode.workspace.onDidChangeTextDocument(e => {
            parseTreeCache.delete(e.document.uri.fsPath);
        })
    );

    context.subscriptions.push(
        vscode.languages.registerHoverProvider('sfml', {
            provideHover(document, position, _token) {
                return getTooltip(document, position);
            }
        })
    );
}

async function getTooltip(document: vscode.TextDocument,position: vscode.Position): Promise<vscode.Hover | undefined> {
    const filePath = document.uri.fsPath;
    if(!filePath.endsWith(".sfm") && !filePath.endsWith(".sfml")) return undefined;
    const text = document.getText();

    try {
        const tree = await parseText(filePath, text);
        const finder = new TooltipFinder(position);
        ParseTreeWalker.DEFAULT.walk(finder as ParseTreeListener, tree);

        if (!finder.result) return undefined;

        const token = finder.result.symbol;
        const definition = TOOLTIP_DEFINITIONS[token.type];
        if (!definition) return undefined;

        const content = new vscode.MarkdownString();
        content.appendMarkdown(definition.description);
        content.appendMarkdown("\n\n**Examples:**\n");

        definition.examples.forEach(example => {
            content.appendCodeblock(example, 'sfml');
        });

        content.isTrusted = true;
        return new vscode.Hover(content);
    } catch (error) {
        console.error('Error generating tooltip:', error);
        return undefined;
    }
}

async function parseText(filePath: string, text: string): Promise<ParseTree> {
    // Usar caché si existe
    if (parseTreeCache.has(filePath)) {
        return parseTreeCache.get(filePath)!;
    }

    // Procesamiento ANTLR
    const inputStream = CharStreams.fromString(text);
    const lexer = new SFMLLexer(inputStream);
    const tokenStream = new CommonTokenStream(lexer);
    const parser = new SFMLParser(tokenStream);

    // Construir árbol de análisis
    const tree = parser.program();
    parseTreeCache.set(filePath, tree);

    return tree;
}