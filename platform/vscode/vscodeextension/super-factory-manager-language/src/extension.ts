import * as vscode from 'vscode';
import { registerSnippets } from './snippets/snippetController';
import { activityBar, deleteTempFiles } from './activitybar/ActivityBar';
import { checkForErrors } from './antlrg4/Error';
import { checkForWarnings } from './antlrg4/Warning';
import { activateTooltip } from './tooltip/tooltip';
import { activaColors } from './syntaxes/syntax';

/**
 * Main method to call everything we need
 * @param context Vscode extension
 */
export function activate(context: vscode.ExtensionContext) {
    console.log("bruh activating");
    registerSnippets(context);
    activityBar(context);
    
    const checking = vscode.workspace.onDidSaveTextDocument((document) => {
        checkForErrors(document);
        checkForWarnings(document);
    });
    context.subscriptions.push(checking);

    activateTooltip(context);
    activaColors(context);
}

export function deactivate() {
    console.log("bruh deactivating"); //Copying teamy, sorry :P
    deleteTempFiles();
}

