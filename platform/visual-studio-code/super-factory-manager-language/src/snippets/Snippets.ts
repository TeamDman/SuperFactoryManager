import * as vscode from 'vscode';

export function combinedSnippets(prefix: string): vscode.Disposable {
    const snippets = [
        // Every Snippet
        {
            trigger: "eve",
            items: [{
                label: 'every ',
                kind: vscode.CompletionItemKind.Snippet,
                insertText: 'every 20 ticks do \n   ${1: }\nend',
                detail: "Provides a every 20 ticks do end"
            }]
        },
        // If Snippets
        {
            trigger: "if",
            items: [
                {
                    label: 'if ',
                    kind: vscode.CompletionItemKind.Snippet,
                    insertText: 'if ${1:boolean_expression} then\n   ${2:code}\nend',
                    detail: 'If statement'
                },
                {
                    label: 'ifelse ',
                    kind: vscode.CompletionItemKind.Snippet,
                    insertText: 'if ${1:boolean_expression} then\n   ${2:code}\nelse\n   ${3:code}\nend',
                    detail: 'If-Else statement'
                },
                {
                    label: 'ifelseif ',
                    kind: vscode.CompletionItemKind.Snippet,
                    insertText: 'if ${1:boolean_expression} then\n   ${2:code}\nelse if ${3:boolean_expression} then\n   ${4:code}\nend',
                    detail: 'If-Else-If statement'
                }
            ]
        },
        // Input Snippet
        {
            trigger: "inpu",
            items: [{
                label: 'input ',
                kind: vscode.CompletionItemKind.Snippet,
                insertText: 'input $1 from ${2:label}',
                detail: 'Input statement'
            }]
        },
        // Output Snippet
        {
            trigger: "outp",
            items: [{
                label: 'output ',
                kind: vscode.CompletionItemKind.Snippet,
                insertText: 'output $1 to ${2:label}',
                detail: 'Output statement'
            }]
        },
        // Basic Snippet
        {
            trigger: "bas",
            items: [{
                label: 'basic ',
                kind: vscode.CompletionItemKind.Snippet,
                insertText: 'name "A program"\n\n' +
                    'every 1 ticks do\n   input fe:: from ${1:power_source}\n   output fe:: to ${2:machine}\nend\n\n' +
                    'every 20 ticks do\n   input from ${3:chest_input}\n   output to ${4:furnace}\nend',
                detail: 'Basic structure for most uses'
            }]
        },
        // Energy Snippet
        {
            trigger: "ener",
            items: [{
                label: 'energy ',
                kind: vscode.CompletionItemKind.Snippet,
                insertText: 'every 1 ticks do\n   input fe:: from ${1:power_source}\n   output fe:: to ${2:machine}\nend',
                detail: 'Create code for energy movement'
            }]
        }
    ];

    const triggerChars = [...new Set([...snippets.flatMap(g => g.trigger.split(''))])].join('');
    const trigger = prefix.trim() === '' ? triggerChars : prefix;
    const provider = vscode.languages.registerCompletionItemProvider(
        { scheme: 'file', language: 'sfml' },
        {
            provideCompletionItems(document: vscode.TextDocument, position: vscode.Position)
            {
                const linePrefix = document.lineAt(position).text.slice(0, position.character).trim().toLowerCase();
                const completions: vscode.CompletionItem[] = [];

                for (const group of snippets) {
                    if(linePrefix.startsWith(group.trigger) || group.trigger.startsWith(linePrefix) || linePrefix.startsWith(trigger))
                    {
                        group.items.forEach(item => {
                            const completion = new vscode.CompletionItem(item.label, item.kind);
                            completion.insertText = new vscode.SnippetString(item.insertText);
                            completion.filterText = group.trigger;
                            if(trigger !== triggerChars)
                            {
                                const range = new vscode.Range(
                                    position.with(undefined, position.character - prefix.length),
                                    position
                                );
                                completion.range = range;
                                completion.filterText = (prefix + group.trigger);
                            }
                            completion.detail = item.detail;
                            completions.push(completion);
                        });
                    }
                }
                if(completions.length > 0)
                {
                    setTimeout(() => vscode.commands.executeCommand('editor.action.triggerSuggest'), 50);
                    return completions;
                }
                return undefined;
            }
        },
        trigger //e, v, i, f, n, p, u, o, t, b, a, s, r or prefix
    );

    return provider;
}
