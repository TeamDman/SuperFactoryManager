/*
 * File for parser and error checking
 */
import * as vscode from 'vscode';
import { CharStreams, CommonTokenStream } from 'antlr4ts';
import { SFMLLexer } from '../generated/SFMLLexer';
import { SFMLParser } from '../generated/SFMLParser';
import { ANTLRErrorListener, RecognitionException, Recognizer } from 'antlr4ts';

// Will clear all errors each time a file is saved, so we don't have duplicated errors or non-existent ones
export const diagnosticCollectionErrors = vscode.languages.createDiagnosticCollection('syntaxErrors');

/**
 * Each time a file is saved, it will trigger the parser, instead of a real-time one (my implementation was so bad)
 * It will clear any existing errors, so we do not have any duplicates
 * @param document File which is saved or being used
 */
export function checkForErrors(document: vscode.TextDocument)
{   
    const enableErrorChecking = vscode.workspace.getConfiguration('sfml').get('enableErrorChecking', false);
    diagnosticCollectionErrors.clear(); // Clear previous errors

    if(!enableErrorChecking) return;

    const diagnostics: vscode.Diagnostic[] = [];

    if(document.languageId === "markdown")
    {
        // Process SFML code blocks in Markdown
        const text = document.getText();
        const codeBlocks = extractSFMLCodeBlocks(text);

        for(const block of codeBlocks)
        {
            const parseResult = parseInput(block.content, block.startLine);
            
            if(!parseResult.success)
            {
                parseResult.errors.forEach((error: any) => {
                    const { lineStart, columnStart, lineEnd, columnEnd, message } = error;

                    // Adjust for the code block's position in the document
                    const adjustedLineStart = lineStart - 1; // Convert to 0-based
                    const adjustedLineEnd = lineEnd - 1; // Convert to 0-based

                    // Gets the start of the line without leading spaces
                    const lineStartText = document.lineAt(adjustedLineStart).text;
                    const firstNonWhitespaceColumn = lineStartText.search(/\S|$/);

                    // Gets the ending of the line
                    const lineEndText = document.lineAt(adjustedLineEnd).text;
                    const lineEndLength = lineEndText.length;

                    // Create range covering the error
                    const range = new vscode.Range(
                        new vscode.Position(adjustedLineStart, firstNonWhitespaceColumn),
                        new vscode.Position(adjustedLineEnd, lineEndLength)
                    );

                    const diagnostic = new vscode.Diagnostic(
                        range, 
                        message, 
                        vscode.DiagnosticSeverity.Error
                    );
                    diagnostics.push(diagnostic);
                });
            }
        }
    }
    else if(document.languageId === "sfm" || document.languageId === "sfml")
    {
        // Process normal SFML files
        const text = document.getText();
        const { success, errors } = parseInput(text);

        if (!success) 
        {
            errors.forEach((error: any) => {
                const { lineStart, columnStart, lineEnd, columnEnd, message } = error;

                //Gets the start of the line without spaces at the beginning, will be the start of the error
                const lineStartText = document.lineAt(lineStart - 1).text;
                const firstNonWhitespaceColumn = lineStartText.search(/\S|$/);  

                //Gets the ending of the line
                const lineEndText = document.lineAt(lineEnd - 1).text;
                const lineEndLength = lineEndText.length;

                //Create range in which the curly error stuff will be
                const range = new vscode.Range(
                    new vscode.Position(lineStart - 1, firstNonWhitespaceColumn),
                    new vscode.Position(lineEnd - 1, lineEndLength)
                );

                //Create the error in which diagnostics will handle
                const diagnostic = new vscode.Diagnostic(range, message, vscode.DiagnosticSeverity.Error);
                diagnostics.push(diagnostic);
            });
        }

    }
    
    diagnosticCollectionErrors.set(document.uri, diagnostics);
}

/**
 * Calls the ANTLR parser to "scan" the text from the saved file.
 * If it has any error, it will output them into an array for handling 
 * @param input Text to parse
 * @param lineOffset Line offset for error adjustment (default 0)
 * @returns Object with success status and array of errors
 */
export function parseInput(input: string, lineOffset: number = 0): { success: boolean, errors: any[] }
{
    const inputStream = CharStreams.fromString(input);
    const lexer = new SFMLLexer(inputStream);
    const tokenStream = new CommonTokenStream(lexer);
    const parser = new SFMLParser(tokenStream);

    const errorListener = new UnderlineErrorListener();
    parser.removeErrorListeners();
    parser.addErrorListener(errorListener);

    const tree = parser.program();
    const errors = errorListener.getErrors();

    // Apply offset to error lines
    const adjustedErrors = errors.map(error => ({
        ...error,
        lineStart: error.lineStart + lineOffset,
        lineEnd: error.lineEnd + lineOffset
    }));

    return {
        success: parser.numberOfSyntaxErrors === 0,
        errors: adjustedErrors
    };
}

/**
 * Extracts SFML code blocks from Markdown documents
 * @param text Markdown document content
 * @returns Array of code blocks with their content and starting line
 */
export function extractSFMLCodeBlocks(text: string): { content: string; startLine: number }[]
{
    const blocks: { content: string; startLine: number }[] = [];
    const lines = text.split('\n');
    let inSfmlBlock = false;
    let currentBlock: string[] = [];
    let startLine = 0;

    for(let i = 0; i < lines.length; i++)
    {
        const line = lines[i];
        
        // Detect SFML block start
        const blockStart = line.match(/```(sfm|sfml)\s*$/);
        if(blockStart && !inSfmlBlock)
        {
            inSfmlBlock = true;
            startLine = i + 1; // Next line is the content start
            continue;
        }
        
        // Detect code block end
        if(inSfmlBlock && line.trim() === '```')
        {
            inSfmlBlock = false;
            blocks.push({
                content: currentBlock.join('\n'),
                startLine: startLine
            });
            currentBlock = [];
            continue;
        }
        
        // Accumulate content within the block
        if(inSfmlBlock) currentBlock.push(line);
    }
    
    // Handle unclosed block
    if(inSfmlBlock && currentBlock.length > 0)
    {
        blocks.push({
            content: currentBlock.join('\n'),
            startLine: startLine
        });
    }
    
    return blocks;
}

// Error listener with position handling
export class UnderlineErrorListener implements ANTLRErrorListener<any> {
    private errors: any[] = [];

    syntaxError<T>(
        recognizer: Recognizer<T, any>,
        offendingSymbol: T,
        lineStart: number,
        columnStart: number,
        msg: string,
        e: RecognitionException | undefined
    ): void {
        const offendingToken = e?.getOffendingToken();
        const lineEnd = offendingToken?.line || lineStart;
        const columnEnd = offendingToken 
            ? offendingToken.charPositionInLine + (offendingToken.text?.length || 0) 
            : columnStart;

        this.errors.push({
            lineStart: lineStart,
            columnStart: columnStart,
            lineEnd: lineEnd,
            columnEnd: columnEnd,
            message: msg
        });
    }

    getErrors() {
        return this.errors;
    }
}