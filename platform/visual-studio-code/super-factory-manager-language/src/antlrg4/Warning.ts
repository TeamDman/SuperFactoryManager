import { CharStreams, CommonTokenStream } from 'antlr4ts';
import { SFMLLexer } from '../generated/SFMLLexer';
import { SFMLParser, BlockContext, ForgetStatementContext, IfStatementContext, InputStatementContext, OutputStatementContext } from '../generated/SFMLParser';
import { SFMLListener } from '../generated/SFMLListener';
import { ParseTreeWalker } from 'antlr4ts/tree/ParseTreeWalker';
import { TextDocument } from 'vscode';
import * as vscode from 'vscode';
import { extractSFMLCodeBlocks } from './Error';

export const diagnosticCollectionWarning = vscode.languages.createDiagnosticCollection('sfml');

class InputOutputChecker implements SFMLListener {
    private inputs: Set<any> = new Set();
    private outputs: Set<any> = new Set();
    private enabled: boolean;
    private onIfElseStatment: boolean = false;
    private diagnostics: vscode.Diagnostic[] = [];
    private document: vscode.TextDocument;
    private lineOffset: number = 0;

    constructor(document: vscode.TextDocument, lineOffset: number = 0) 
    {
        this.document = document;
        this.lineOffset = lineOffset;
        this.enabled = vscode.workspace.getConfiguration('sfml').get('enableWarningChecking', true);
    }

    /**
     * Checks if a corresponding input or output has its corresponding counterpart
     */
    private verifyInputsAndOutputs() {
        // console.log("Inputs: ", this.inputs);
        // console.log("Outputs: ", this.outputs);

        this.inputs.forEach(input => {
            if(!Array.from(this.outputs).some(output => output.type === input.type))
            {
                const range = this.calculateRange(input);
                const message = `Warning: Input ${input.type}:: without corresponding output.`;
                const diagnostic = new vscode.Diagnostic(range, message, vscode.DiagnosticSeverity.Warning);
                this.diagnostics.push(diagnostic);
            }
        });

        this.outputs.forEach(output => {
            if(!Array.from(this.inputs).some(input => input.type === output.type))
            {
                const range = this.calculateRange(output);
                const message = `Warning: Output ${output.type}:: without corresponding input.`;
                const diagnostic = new vscode.Diagnostic(range, message, vscode.DiagnosticSeverity.Warning);
                this.diagnostics.push(diagnostic);
            }
        });

        diagnosticCollectionWarning.set(this.document.uri, this.diagnostics);
    }

    //Get the range, from the first no space to the last letter
    private calculateRange(lineData: { start: any, stop: any }): vscode.Range
    {
        const startLine = this.lineOffset + lineData.start.line - 1;
        const endLine = this.lineOffset + lineData.stop.line - 1;
        const lineText = this.document.lineAt(startLine).text;
        const firstNonWhitespaceIndex = lineText.search(/\S/);
        const lastNonWhitespaceIndex = lineText.trimEnd().length;

        return new vscode.Range(
            new vscode.Position(startLine, firstNonWhitespaceIndex),
            new vscode.Position(endLine, lastNonWhitespaceIndex)
        );
    }

    public clearDiagnostics()
    {
        this.diagnostics = [];
        diagnosticCollectionWarning.delete(this.document.uri);
    }

    //Inputs statments
    enterInputStatement(ctx: InputStatementContext) 
    {
        if(!this.enabled) return;
        
        //Blame ctx.text because it deletes all spaces
        let inputType = ctx.text.match(/(fe|fluid|gas|item)(?:::[^:]*|:[^:*]*:\*|:[^:*]*)/i)?.[1]?.toLowerCase();
        
        // If we dont find anything above, we consider it item::
        if(!inputType || !ctx.text.includes(":")) inputType = 'item';

        if(inputType.startsWith("fluid:")) inputType = "fluid"
        if(inputType.startsWith("fe:")) inputType = "fe"
        if(inputType.startsWith("gas:")) inputType = "gas"
        if(inputType.startsWith("item:")) inputType = "item"
        const line = {
            type: inputType,
            start: ctx.start,
            stop: ctx.stop
        }
        this.inputs.add(line);
    }

    //Output statments
    enterOutputStatement(ctx: OutputStatementContext) 
    {
        if(!this.enabled) return;
        
        //Blame ctx.text because it deletes all spaces
        let outputType = ctx.text.match(/(fe|fluid|gas|item)(?:::[^:]*|:[^:*]*:\*|:[^:*]*)/i)?.[1]?.toLowerCase();
    
        // If we dont find anything above, we consider it item::
        if(!outputType || !ctx.text.includes(":")) outputType = 'item';
        
        if(outputType.startsWith("fluid:")) outputType = "fluid"
        if(outputType.startsWith("fe:")) outputType = "fe"
        if(outputType.startsWith("gas:")) outputType = "gas"
        if(outputType.startsWith("item:")) outputType = "item"
        const line = {
            type: outputType,
            start: ctx.start,
            stop: ctx.stop
        }
        this.outputs.add(line);
    }

    //Forget everything before, and start the handling of warnings
    //After that, we dont care about what comes before and we clear everything
    enterForgetStatement(ctx: ForgetStatementContext) 
    {
        if(!this.enabled) return;
        
        this.verifyInputsAndOutputs();
        console.log("Forget statment");
        this.inputs.clear();
        this.outputs.clear();
    }

    //If on an ifStatment, it will do nothing, because we dont exit
    //If we are not inside one, we ended that block
    exitBlock(ctx: BlockContext) 
    {
        if(!this.enabled) return;
        
        if(this.onIfElseStatment)
        {
            this.onIfElseStatment = false;
            return;
        }
        this.verifyInputsAndOutputs();
        this.inputs.clear();
        this.outputs.clear();
    }

    enterIfStatement(ctx: IfStatementContext)
    {
        if(!this.enabled) return;
        this.onIfElseStatment = true;
    }

    // Final check at the end of parsing
    public finalCheck()
    {
        if (!this.enabled) return;
        this.verifyInputsAndOutputs();
        this.inputs.clear();
        this.outputs.clear();
    }

    // Do not remove, walker.walk errors if we delete those
    enterEveryRule?(ctx: any): void {}
    exitEveryRule?(ctx: any): void {}
    visitTerminal?(node: any): void {}
    visitErrorNode?(node: any): void {}
}


// Función para analizar el código
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
        // Procesar bloques de código SFML en Markdown
        const text = document.getText();
        const blocks = extractSFMLCodeBlocks(text);

        for(const block of blocks)
        {
            const inputStream = CharStreams.fromString(block.content);
            const lexer = new SFMLLexer(inputStream);
            const tokenStream = new CommonTokenStream(lexer);
            const parser = new SFMLParser(tokenStream);

            const tree = parser.program();
            const checker = new InputOutputChecker(document, block.startLine);
            const walker = new ParseTreeWalker();
            walker.walk(checker, tree);
            checker.finalCheck(); // Final check after parsing
        }
    } 
    else if(document.languageId === 'sfml' || document.languageId === 'sfm')
    {
        // Procesar archivos SFML normales
        const inputStream = CharStreams.fromString(document.getText());
        const lexer = new SFMLLexer(inputStream);
        const tokenStream = new CommonTokenStream(lexer);
        const parser = new SFMLParser(tokenStream);

        const tree = parser.program();
        const checker = new InputOutputChecker(document);
        const walker = new ParseTreeWalker();
        walker.walk(checker, tree);
        checker.finalCheck(); // Final check after parsing
    }
}