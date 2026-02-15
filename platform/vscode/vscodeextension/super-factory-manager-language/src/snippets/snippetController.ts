import * as vscode from 'vscode';
import { combinedSnippets } from './Snippets';

/**
 * Handle everything related to snippets completion.
 * @param context 
 */
export function registerSnippets(context: vscode.ExtensionContext) {
    let registeredProviders: vscode.Disposable[] = [];
    
    //Register all snippets, depends on if you have a prefix or not
    const registerSnippetProviders = (snippetPrefix: string) => {
        clearSnippetProviders()
        registeredProviders.push(combinedSnippets(snippetPrefix));
        registeredProviders.forEach(provider => context.subscriptions.push(provider));
    };

    const clearSnippetProviders = () => {
        //Delete the previous subscription from context.subs
        //Why? you dont want to have 1000 undefined ones when you change stuff
        registeredProviders.forEach(provider => {
            const index = context.subscriptions.indexOf(provider);
            if (index > -1) {
                context.subscriptions.splice(index, 1);
            }
        });
    
        //Clear the list
        registeredProviders.forEach(provider => provider.dispose());
        registeredProviders = [];
    };

    const snippetPrefix = vscode.workspace.getConfiguration('sfml').get<string>('SnippetActivation') || ' '; // If there isnt one, use a default one
    const snippetsEnabled = vscode.workspace.getConfiguration('sfml').get<boolean>('enableSnippets'); // Check if snippets are enabled

    if(snippetsEnabled) 
    {
        registerSnippetProviders(snippetPrefix);
    }
    else 
    {
        clearSnippetProviders();
    }

    //Check on any changes affecting those 2 settings
    vscode.workspace.onDidChangeConfiguration((event) => {
        if (event.affectsConfiguration('sfml.enableSnippets') || event.affectsConfiguration('sfml.SnippetActivation')) {
            const newSnippetsEnabled = vscode.workspace.getConfiguration('sfml').get<boolean>('enableSnippets');
            const newPrefix = vscode.workspace.getConfiguration('sfml').get<string>('SnippetActivation') || ' ';

            if(newSnippetsEnabled) 
            {
                registerSnippetProviders(newPrefix);
            } 
            else 
            {
                clearSnippetProviders();
            }
        }
    });
}
