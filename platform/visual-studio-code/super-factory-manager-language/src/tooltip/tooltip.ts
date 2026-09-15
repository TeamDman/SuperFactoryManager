import * as vscode from 'vscode';
import { CharStreams, CommonTokenStream } from 'antlr4ts';
import { ParseTree, TerminalNode, ParseTreeWalker, ParseTreeListener } from 'antlr4ts/tree';
import { Token } from 'antlr4ts/Token';
import { SFMLLexer } from '../generated/SFMLLexer';
import { SFMLParser } from '../generated/SFMLParser';

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
    [SFMLLexer.THEN]: {
        description: "**THEN**\n\nMarks the start of the action block in a conditional statement",
        examples: [
            "if redstone > 5 then ... end"
        ]
    },
    [SFMLLexer.ELSE]: {
        description: "**ELSE**\n\nOptional branch for IF statements when the condition is false",
        examples: [
            "if redstone > 5 then ... else ... end"
        ]
    },
    [SFMLLexer.HAS]: {
        description: "**HAS**\n\nChecks if an inventory or target contains specific items, fluids, or quantities",
        examples: [
            "if chest has > 10 coal then ... end",
            "if chest has fluid::lava then ... end"
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
    [SFMLLexer.LONE]: {
        description: "**LONE**\n\nChecks if exactly one or zero elements match the condition",
        examples: [
            "if lone chest has > 64 coal then ... end"
        ]
    },
    [SFMLLexer.TRUE]: {
        description: "**TRUE**\n\nBoolean constant representing a true value",
        examples: [
            "if true then ... end"
        ]
    },
    [SFMLLexer.FALSE]: {
        description: "**FALSE**\n\nBoolean constant representing a false value",
        examples: [
            "if false then ... end"
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
            "if redstone < 15 then ... end",
            "if chest has < 64 coal then ... end"
        ]
    },
    [SFMLLexer.EQ]: {
        description: "**= (EQ)**\n\nEquality comparison",
        examples: [
            "if redstone eq 10 then ... end",
            "if chest has eq 0 coal then ... end"
        ]
    },
    [SFMLLexer.EQ_SYMBOL]: {
        description: "**= (EQ)**\n\nEquality comparison",
        examples: [
            "if redstone = 10 then ... end",
            "if chest has = 0 coal then ... end"
        ]
    },
    [SFMLLexer.LE]: {
        description: "**<= (LE)**\n\nLess than or equal comparison",
        examples: [
            "if redstone le 7 then ... end",
            "if chest has le 32 coal then ... end"
        ]
    },
    [SFMLLexer.LE_SYMBOL]: {
        description: "**<= (LE)**\n\nLess than or equal comparison",
        examples: [
            "if redstone <= 7 then ... end",
            "if chest has <= 32 coal then ... end"
        ]
    },
    [SFMLLexer.GE]: {
        description: "**>= (GE)**\n\nGreater than or equal comparison",
        examples: [
            "if redstone ge 12 then ... end",
            "if chest has ge 64 coal then ... end"
        ]
    },
    [SFMLLexer.GE_SYMBOL]: {
        description: "**>= (GE)**\n\nGreater than or equal comparison",
        examples: [
            "if redstone >= 12 then ... end",
            "if chest has >= 64 coal then ... end"
        ]
    },
    [SFMLLexer.FROM]: {
        description: "**FROM**\n\nSpecifies the source inventory or container for an extraction operation",
        examples: [
            "input from chest",
            "from chest input coal"
        ]
    },
    [SFMLLexer.TO]: {
        description: "**TO**\n\nSpecifies the destination inventory or container for an insertion operation",
        examples: [
            "output to chest",
            "to furnace output coal"
        ]
    },
    [SFMLLexer.INPUT]: {
        description: "**INPUT**\n\nExtracts contents from an inventory",
        examples: [
            "input from chest",
            "input 64 coal from chest"
        ]
    },
    [SFMLLexer.OUTPUT]: {
        description: "**OUTPUT**\n\nSends contents to an inventory",
        examples: [
            "output to chest",
            "output retain 4 coal to furnace"
        ]
    },
    [SFMLLexer.WHERE]: {
        description: "**WHERE**\n\nReserved filter keyword",
        examples: [
            "input where item = minecraft:dirt"
        ]
    },
    [SFMLLexer.SLOTS]: {
        description: "**SLOTS**\n\nSpecifies a range or list of inventory slots",
        examples: [
            "output to furnace slots 1-3",
            "input from chest slots 5,9,13"
        ]
    },
    [SFMLLexer.SLOT]: {
        description: "**SLOT**\n\nSpecifies a single inventory slot",
        examples: [
            "output to furnace slot 1",
            "input from chest slot 0"
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
        description: "**EACH**\n\nApplies the operation to every matching element or side",
        examples: [
            "input from each chest",
            "input from machine each side"
        ]
    },
    [SFMLLexer.EXCEPT]: {
        description: "**EXCEPT**\n\nExcludes specific items, fluids, gas, or energy from the operation",
        examples: [
            "input * except cobblestone, dirt from chest",
            "output fluid:: except fluid::lava to interface"
        ]
    },
    [SFMLLexer.FORGET]: {
        description: "**FORGET**\n\nClears previous inputs or specific labels",
        examples: [
            "forget",
            "forget chest",
            "forget chest, furnace"
        ]
    },
    [SFMLLexer.EMPTY]: {
        description: "**EMPTY**\n\nUsed with `IN` to target empty inventory slots",
        examples: [
            "output to empty slots in chest",
            "output to empty slot in furnace"
        ]
    },
    [SFMLLexer.IN]: {
        description: "**IN**\n\nSpecifies the container for empty slots target",
        examples: [
            "output to empty slots in chest",
            "output to empty slot in furnace"
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
        description: "**WITH**\n\nFilters items having the specified tags or conditions",
        examples: [
            "input with #minecraft:logs",
            "output with #c:my_super_dupper_tag"
        ]
    },
    [SFMLLexer.TAG]: {
        description: "**TAG**\n\nKeyword used to filter by tag matcher",
        examples: [
            "input with tag #minecraft:logs",
            "input with tag minecraft:logs"
        ]
    },
    [SFMLLexer.HASHTAG]: {
        description: "**# (HASHTAG)**\n\nPrefix character used to declare tag matchers",
        examples: [
            "input with #minecraft:logs",
            "output with tag #c:ores"
        ]
    },
    [SFMLLexer.ROUND]: {
        description: "**ROUND ROBIN BY**\n\nDistributes items sequentially by label or block",
        examples: [
            "output to chest round robin by block",
            "input from interface1, interface2 round robin by label"
        ]
    },
    [SFMLLexer.ROBIN]: {
        description: "**ROUND ROBIN BY**\n\nDistributes items sequentially by label or block",
        examples: [
            "output to chest round robin by block",
            "input from interface1, interface2 round robin by label"
        ]
    },
    [SFMLLexer.BY]: {
        description: "**ROUND ROBIN BY**\n\nDistributes items sequentially by label or block",
        examples: [
            "output to chest round robin by block",
            "input from interface1, interface2 round robin by label"
        ]
    },
    [SFMLLexer.LABEL]: {
        description: "**LABEL**\n\nSpecifies distribution mode by label",
        examples: [
            "output to chest round robin by label"
        ]
    },
    [SFMLLexer.BLOCK]: {
        description: "**BLOCK**\n\nSpecifies distribution mode by block",
        examples: [
            "output to chest round robin by block"
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
    [SFMLLexer.LEFT]: {
        description: "**LEFT**\n\nSpecifies the left side of a block",
        examples: [
            "input from machine left side",
            "output to furnace left slots 1-3"
        ]
    },
    [SFMLLexer.RIGHT]: {
        description: "**RIGHT**\n\nSpecifies the right side of a block",
        examples: [
            "input from machine right side",
            "output to furnace right slots 1-3"
        ]
    },
    [SFMLLexer.FRONT]: {
        description: "**FRONT**\n\nSpecifies the front side of a block",
        examples: [
            "input from machine front side",
            "output to furnace front slots 1-3"
        ]
    },
    [SFMLLexer.BACK]: {
        description: "**BACK**\n\nSpecifies the back side of a block",
        examples: [
            "input from machine back side",
            "output to furnace back slots 1-3"
        ]
    },
    [SFMLLexer.SIDE]: {
        description: "**SIDE**\n\nSpecifies direction(s) for the operation",
        examples: [
            "input from machine top side",
            "output to furnace bottom slots 1-3",
            "input from interface each side"
        ]
    },
    [SFMLLexer.NULL]: {
        description: "**NULL**\n\nSpecifies an unassigned or null side",
        examples: [
            "input from machine null side"
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
        description: "**TICK**\n\nRepresents one tick",
        examples: [
            "every 1 tick do ... end",
            "every tick do ... end"
        ]
    },
    [SFMLLexer.SECOND]: {
        description: "**SECOND**\n\nTime unit (represents 20 ticks)",
        examples: [
            "every 1 second do ... end",
            "every second do ... end"
        ]
    },
    [SFMLLexer.SECONDS]: {
        description: "**SECONDS**\n\nTime unit in seconds",
        examples: [
            "every 2 seconds do ... end",
            "every 50 seconds do ... end"
        ]
    },
    [SFMLLexer.GLOBAL]: {
        description: "**GLOBAL (o G)**\n\nModificador opcional para intervalos de tiempo globales",
        examples: [
            "every 5 global ticks do ... end",
            "every 10g seconds do ... end"
        ]
    },
    [SFMLLexer.PLUS]: {
        description: "**PLUS (o +)**\n\nSuma un desplazamiento numérico adicional al intervalo del timer",
        examples: [
            "every 10 + 2 ticks do ... end",
            "every 5 plus 1 second do ... end"
        ]
    },
    [SFMLLexer.REDSTONE]: {
        description: "**REDSTONE**\n\nReferences redstone power level or trigger on the manager block",
        examples: [
            "if redstone > 0 then ... end",
            "every redstone pulse do ... end"
        ]
    },
    [SFMLLexer.PULSE]: {
        description: "**PULSE**\n\nTriggers on redstone signal changes on the manager block",
        examples: [
            "every redstone pulse do ... end"
        ]
    },
    [SFMLLexer.DO]: {
        description: "**DO**\n\nMarks the beginning of an executable trigger block",
        examples: [
            "every 5 ticks do ... end",
            "every redstone pulse do ... end"
        ]
    },
    [SFMLLexer.END]: {
        description: "**END**\n\nCloses an open block (IF, EVERY, etc.)",
        examples: [
            "if redstone > 0 then ... end",
            "every 5 ticks do ... end"
        ]
    },
    [SFMLLexer.NAME]: {
        description: "**NAME**\n\nNames the current program (optional header)",
        examples: [
            "name \"My super dupper laggy program\"",
            "name \"Redstone factory v3\""
        ]
    },
    [SFMLLexer.EVERY]: {
        description: "**EVERY**\n\nCreates a timed or event trigger block",
        examples: [
            "every 5 ticks do ... end",
            "every redstone pulse do ... end"
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