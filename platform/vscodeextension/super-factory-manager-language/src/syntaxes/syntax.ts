import * as vscode from 'vscode';
import { CharStreams, Token } from 'antlr4ts';
import { SFMLLexer } from '../generated/SFMLLexer';
import { extractSFMLCodeBlocks } from '../antlrg4/Error';

export class SemanticTokensProvider implements vscode.DocumentSemanticTokensProvider
{
    public legend = new vscode.SemanticTokensLegend( //these are all available items
        [
            'comment', 'string', 'keyword', 'number', 'regexp', 'operator', 'namespace',
            'type', 'struct', 'class', 'interface', 'enum', 'typeParameter', 'function',
            'method', 'decorator', 'macro', 'variable', 'parameter', 'property', 'label',
            'invalid'
        ],  //Types of tokens
        [
            'declaration', 'documentation', 'readonly', 'static', 'abstract', 'deprecated',
            'modification', 'async'
        ]   //Modifiers
    );
    
    //ANTLR numbers to semantic types
    //antlr keyworks like SFMLLexer.IF is just a number so, we can say
    //number 1 equals to keyword

    /*
    For anyone needing to modify this on the future, if you need to add
    just add to the specific zone
    if you need to change, change to the correct zone :p
    */
    public tokenTypes = new Map<number, string>([
        //keywork zone
        [SFMLLexer.IF, 'keyword'],
        [SFMLLexer.INPUT, 'keyword'],
        [SFMLLexer.ELSE, 'keyword'],
        [SFMLLexer.THEN, 'keyword'],
        [SFMLLexer.HAS, 'keyword'],
        [SFMLLexer.FROM, 'keyword'],
        [SFMLLexer.TO, 'keyword'],
        [SFMLLexer.OUTPUT, 'keyword'],
        [SFMLLexer.WHERE, 'keyword'],
        [SFMLLexer.SLOTS, 'keyword'],
        [SFMLLexer.RETAIN, 'keyword'],
        [SFMLLexer.EACH, 'keyword'],
        [SFMLLexer.EXCEPT, 'keyword'],
        [SFMLLexer.WITH, 'keyword'],
        [SFMLLexer.WITHOUT, 'keyword'],
        [SFMLLexer.SIDE, 'keyword'],
        [SFMLLexer.PULSE, 'keyword'],
        [SFMLLexer.DO, 'keyword'],
        [SFMLLexer.END, 'keyword'],
        [SFMLLexer.NAME, 'keyword'],
        [SFMLLexer.EVERY, 'keyword'],

        //number zone
        [SFMLLexer.NUMBER, 'number'],
        [SFMLLexer.OVERALL, 'number'],
        [SFMLLexer.SOME, 'number'],
        [SFMLLexer.ONE, 'number'],
        [SFMLLexer.LONE, 'number'],
        [SFMLLexer.TRUE, 'number'],
        [SFMLLexer.FALSE, 'number'],
        [SFMLLexer.NOT, 'number'],
        [SFMLLexer.AND, 'number'],
        [SFMLLexer.OR, 'number'],
        [SFMLLexer.GT, 'number'],
        [SFMLLexer.GT_SYMBOL, 'number'],
        [SFMLLexer.LT, 'number'],
        [SFMLLexer.LT_SYMBOL, 'number'],
        [SFMLLexer.EQ, 'number'],
        [SFMLLexer.EQ_SYMBOL, 'number'],
        [SFMLLexer.LE, 'number'],
        [SFMLLexer.LE_SYMBOL, 'number'],
        [SFMLLexer.GE, 'number'],
        [SFMLLexer.GE_SYMBOL, 'number'],
        [SFMLLexer.FORGET, 'number'],
        [SFMLLexer.LABEL, 'number'],
        [SFMLLexer.BLOCK, 'number'],
        [SFMLLexer.TOP, 'number'],
        [SFMLLexer.BOTTOM, 'number'],
        [SFMLLexer.NORTH, 'number'],
        [SFMLLexer.EAST, 'number'],
        [SFMLLexer.SOUTH, 'number'],
        [SFMLLexer.WEST, 'number'],

        //string zone
        [SFMLLexer.STRING, 'string'],
        [SFMLLexer.ROUND, 'string'],
        [SFMLLexer.ROBIN, 'string'],
        [SFMLLexer.BY, 'string'],
        [SFMLLexer.TICKS, 'string'],
        [SFMLLexer.TICK, 'string'],
        [SFMLLexer.SECONDS, 'string'],
        [SFMLLexer.SECOND, 'string'],
        [SFMLLexer.GLOBAL, 'string'],
        [SFMLLexer.PLUS, 'string'],
        //comments zone
        [SFMLLexer.LINE_COMMENT, 'comment'],

        //parameter zone
        [SFMLLexer.IDENTIFIER, 'parameter'],
        [SFMLLexer.HASHTAG, 'parameter'],
        [SFMLLexer.COLON, 'parameter'],

    ]);

    //things that could be improved, this should range from 80 ms (cold start) to 3 ms (after some time)
    //measurements were made using a decent pc, not a potato one (needs testing)
    //-- comments, to take from -- to the end of the line
    //use a better semantic tokens builder
    // on a node worker? basically on another thread 
    provideDocumentSemanticTokens(document: vscode.TextDocument, token: vscode.CancellationToken): vscode.ProviderResult<vscode.SemanticTokens>
    {
        const builder = new vscode.SemanticTokensBuilder(this.legend);

        if(document.languageId === 'sfml')
        {
            const text = document.getText();
            const inputStream = CharStreams.fromString(text);
            const lexer = new SFMLLexer(inputStream);

            let antlrToken: Token;
            while((antlrToken = lexer.nextToken()) && antlrToken.type !== Token.EOF)
            {
                if(token.isCancellationRequested) break;
                
                const tokenType = this.tokenTypes.get(antlrToken.type);
                if(!tokenType) continue;

                const startPos = document.positionAt(antlrToken.startIndex);
                const endPos = document.positionAt(antlrToken.stopIndex + 1);
                
                builder.push(
                    new vscode.Range(startPos, endPos),
                    tokenType
                );
            }
        }
        else if(document.languageId === 'markdown')
        {
            const text = document.getText();
            const blocks = extractSFMLCodeBlocks(text);

            for(const block of blocks)
            {
                if(token.isCancellationRequested) break;
                
                const inputStream = CharStreams.fromString(block.content);
                const lexer = new SFMLLexer(inputStream);

                let antlrToken: Token;
                while((antlrToken = lexer.nextToken()) && antlrToken.type !== Token.EOF)
                {
                    if (token.isCancellationRequested) break;
                    
                    const tokenType = this.tokenTypes.get(antlrToken.type);
                    if(!tokenType) continue;

                    //We need to make a few extract stuff to hit the correct line
                    const tokenLine = block.startLine + antlrToken.line - 1;
                    const tokenStartCol = antlrToken.charPositionInLine;
                    const tokenEndCol = tokenStartCol + (antlrToken.text?.length || 0);

                    const startPos = new vscode.Position(tokenLine, tokenStartCol);
                    const endPos = new vscode.Position(tokenLine, tokenEndCol);
                    
                    builder.push(
                        new vscode.Range(startPos, endPos),
                        tokenType
                    );
                }
            }
        }

        return builder.build();
    }

    //turns out that we can gain a few ms if we do this, idk why
    //around 2 or 3, everything is welcomed
    provideDocumentSemanticTokensEdits(document: vscode.TextDocument, previousResultId: string, token: vscode.CancellationToken): vscode.ProviderResult<vscode.SemanticTokens | vscode.SemanticTokensEdits>
    {
        return this.provideDocumentSemanticTokens(document, token);
    }
}

export function activaColors(context: vscode.ExtensionContext)
{
    const sfmlSelector = { language: 'sfml', scheme: 'file' };
    const markdownSelector = { language: 'markdown', scheme: 'file' };
    
    const provider = new SemanticTokensProvider();

    context.subscriptions.push(
        vscode.languages.registerDocumentSemanticTokensProvider(
            sfmlSelector,
            provider,
            provider.legend
        ),
        vscode.languages.registerDocumentSemanticTokensProvider(
            markdownSelector,
            provider,
            provider.legend
        )
    );
}