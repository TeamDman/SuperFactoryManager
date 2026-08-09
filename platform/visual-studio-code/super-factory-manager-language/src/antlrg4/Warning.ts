import { CharStreams, CommonTokenStream, ParserRuleContext } from 'antlr4ts';
import { SFMLLexer } from '../generated/SFMLLexer';
import { SFMLParser, BlockContext, ForgetStatementContext, InputStatementContext, OutputStatementContext, IfStatementContext } from '../generated/SFMLParser';
import { SFMLListener } from '../generated/SFMLListener';
import { ParseTreeWalker } from 'antlr4ts/tree/ParseTreeWalker';
import { TextDocument } from 'vscode';
import * as vscode from 'vscode';
import { extractSFMLCodeBlocks } from './Error';
import { TerminalNode } from 'antlr4ts/tree/TerminalNode';
import { ErrorNode } from 'antlr4ts/tree/ErrorNode';

export const diagnosticCollectionWarning = vscode.languages.createDiagnosticCollection('sfml');

class InputOutputChecker implements SFMLListener
{
    private inputStack: Map<string, any[]>[] = [];
    private outputStack: Map<string, any[]>[] = [];
    private ifStack: { 
        branches: { 
            inputs: Map<string, any[]>, 
            outputs: Map<string, any[]> 
        }[] 
    }[] = [];
    
    private enabled: boolean;
    private diagnostics: vscode.Diagnostic[] = [];
    private document: vscode.TextDocument;
    private lineOffset: number = 0;

    constructor(document: vscode.TextDocument, lineOffset: number = 0) 
    {
        this.document = document;
        this.lineOffset = lineOffset;
        this.enabled = vscode.workspace.getConfiguration('sfml').get('enableWarningChecking', true);
    }

    private getResourceType(ctx: any): string
    {
        const text = ctx.text.toLowerCase();
        if (text.includes("fluid:")) return "fluid";
        if (text.includes("gas:"))   return "gas";
        if (text.includes("fe:"))    return "fe";
        return "item"; // Default en SFM
    }

    private addStatement(map: Map<string, any[]>, ctx: any)
    {
        const type = this.getResourceType(ctx);
        const existing = map.get(type) || [];
        existing.push(ctx);
        map.set(type, existing);
    }

    private verifyInputsAndOutputs(inputs: Map<string, any[]>, outputs: Map<string, any[]>)
    {
        if(!this.enabled) return;

        inputs.forEach((contexts, type) => {
            if(!outputs.has(type))
            {
                contexts.forEach(ctx => {
                    this.pushDiagnostic(ctx, `Input of type '${type}' has no matching output in this block.`);
                });
            }
        });

        outputs.forEach((contexts, type) => {
            if(!inputs.has(type))
            {
                contexts.forEach(ctx => {
                    this.pushDiagnostic(ctx, `Output of type '${type}' has no matching input in this block.`);
                });
            }
        });
    }

    private pushDiagnostic(ctx: any, message: string)
    {
        const range = this.calculateRange(ctx);
        const diagnostic = new vscode.Diagnostic(
            range, 
            `SFM Linter: ${message}`, 
            vscode.DiagnosticSeverity.Warning
        );
        diagnostic.code = 'missing-counterpart';
        this.diagnostics.push(diagnostic);
    }

    public updateUI()
    {
        diagnosticCollectionWarning.set(this.document.uri, this.diagnostics);
    }

    // SFML Listener methods
    enterBlock(ctx: BlockContext)
    {
        if (!this.enabled) return;
        this.inputStack.push(new Map());
        this.outputStack.push(new Map());
    }

    exitBlock(ctx: BlockContext) {
        if(!this.enabled) return;
        if(this.inputStack.length === 0) return;

        if(ctx.parent instanceof IfStatementContext)
        {
            // Pop the branch maps and store them in the current IF context
            const branchInputs = this.inputStack.pop()!;
            const branchOutputs = this.outputStack.pop()!;
            const currentIf = this.ifStack[this.ifStack.length - 1];
            currentIf.branches.push({ inputs: branchInputs, outputs: branchOutputs });
        }
        else
        {
            // Normal block: verify and pop
            const inputs = this.inputStack.pop()!;
            const outputs = this.outputStack.pop()!;
            this.verifyInputsAndOutputs(inputs, outputs);
        }
    }

    enterInputStatement(ctx: InputStatementContext)
    {
        if (!this.enabled) return;
        if (this.inputStack.length === 0) return; // No block context – ignore or handle as needed
        const currentInputs = this.inputStack[this.inputStack.length - 1];
        this.addStatement(currentInputs, ctx);
    }

    enterOutputStatement(ctx: OutputStatementContext)
    {
        if(!this.enabled) return;
        if(this.outputStack.length === 0) return;
        const currentOutputs = this.outputStack[this.outputStack.length - 1];
        this.addStatement(currentOutputs, ctx);
    }

    enterForgetStatement(ctx: ForgetStatementContext)
    {
        if(!this.enabled) return;
        if(this.inputStack.length === 0) return;
        const childrenCount = ctx.childCount;
        if(childrenCount === 1) // Simple FORGET (no labels)
        {
            const currentInputs = this.inputStack[this.inputStack.length - 1];
            const currentOutputs = this.outputStack[this.outputStack.length - 1];
            this.verifyInputsAndOutputs(currentInputs, currentOutputs);
            currentInputs.clear();
            currentOutputs.clear();
        }
    }

    enterIfStatement(ctx: IfStatementContext)
    {
        if(!this.enabled) return;
        this.ifStack.push({ branches: [] });
    }

    exitIfStatement(ctx: IfStatementContext)
    {
        if(!this.enabled) return;
        const ifCtx = this.ifStack.pop()!;
        // Merge all branches into the current outer maps
        const outerInputs = this.inputStack[this.inputStack.length - 1];
        const outerOutputs = this.outputStack[this.outputStack.length - 1];
        for(const branch of ifCtx.branches)
        {
            this.mergeMaps(outerInputs, branch.inputs);
            this.mergeMaps(outerOutputs, branch.outputs);
        }
    }

    private mergeMaps(target: Map<string, any[]>, source: Map<string, any[]>)
    {
        source.forEach((contexts, type) => {
            const existing = target.get(type) || [];
            target.set(type, existing.concat(contexts));
        });
    }

    enterEveryRule(ctx: ParserRuleContext): void {}
    exitEveryRule(ctx: ParserRuleContext): void {}
    visitTerminal(node: TerminalNode): void {}
    visitErrorNode(node: ErrorNode): void {}

    public finalCheck()
    {
        // If any maps remain (e.g., top‑level statements without a block), verify them.
        if(this.enabled && this.inputStack.length > 0)
        {
            const inputs = this.inputStack[this.inputStack.length - 1];
            const outputs = this.outputStack[this.outputStack.length - 1];
            this.verifyInputsAndOutputs(inputs, outputs);
        }
    }

    private calculateRange(ctx: any): vscode.Range
    {
        const startLine = this.lineOffset + ctx.start.line - 1;
        const endLine = this.lineOffset + ctx.stop.line - 1;
        const lineText = this.document.lineAt(startLine).text;
        
        return new vscode.Range(
            new vscode.Position(startLine, lineText.search(/\S/)),
            new vscode.Position(endLine, lineText.trimEnd().length)
        );
    }

    public clearDiagnostics()
    {
        this.diagnostics = [];
        diagnosticCollectionWarning.delete(this.document.uri);
    }
}

export function checkForWarnings(document: TextDocument) 
{
    const enableWarningChecking = vscode.workspace.getConfiguration('sfml').get('enableWarningChecking', false);
    if(!enableWarningChecking)
    {
        diagnosticCollectionWarning.delete(document.uri);
        return;
    }

    diagnosticCollectionWarning.delete(document.uri); // Clear previous warnings

    if(document.languageId === 'markdown')
    {
        const text = document.getText();
        const blocks = extractSFMLCodeBlocks(text);
        blocks.forEach(block => {
            const inputStream = CharStreams.fromString(block.content);
            const lexer = new SFMLLexer(inputStream);
            const tokenStream = new CommonTokenStream(lexer);
            const parser = new SFMLParser(tokenStream);

            const tree = parser.program();
            const checker = new InputOutputChecker(document, block.startLine);
            const walker = new ParseTreeWalker();
            walker.walk(checker, tree);
            checker.finalCheck();
            checker.updateUI();
        })
    } 
    else if(document.languageId === 'sfml' || document.languageId === 'sfm')
    {
        const inputStream = CharStreams.fromString(document.getText());
        const lexer = new SFMLLexer(inputStream);
        const tokenStream = new CommonTokenStream(lexer);
        const parser = new SFMLParser(tokenStream);

        const tree = parser.program();
        const checker = new InputOutputChecker(document);
        const walker = new ParseTreeWalker();
        walker.walk(checker, tree);
        checker.finalCheck();
        checker.updateUI();
    }
}