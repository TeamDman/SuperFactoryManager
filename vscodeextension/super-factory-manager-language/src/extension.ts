import * as vscode from 'vscode';

export function activate(context: vscode.ExtensionContext) {
    vscode.window.showInformationMessage('POWAH!!!!!');
}

export function deactivate() {}