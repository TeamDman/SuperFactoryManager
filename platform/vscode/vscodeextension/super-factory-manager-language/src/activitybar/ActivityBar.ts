import * as vscode from 'vscode';
import * as fs from 'fs';
import { getOpenCommand, SFMLTreeDataProvider } from './SFMLTreeDataProvider';

//Note: it would be faster if we have already on the folder media or similar, but things can change
// and wanted to make it so its always up-to-date - Titop54

/**
 * For each file we download from github (the content, not which file is), we want to have a record, 
 * so we can delete them later when extension calls deactivate() on extension.ts
 */
const tempFiles: Map<string, string> = new Map();

/**
 * When wanting to check an example, it will download a .json from github containing all folders
 * and files on said directory, which is the example one. 
 * This only download the "how many files and folder", but not their contents
 * If we click on any of those, it then will download the actual content on a temp file
 * on the tmp folder of your os.
 * When deactivate() is called, it will delete those file, because we dont want it after we
 * close vscode  
 * @param context VSCode extension
 */
export async function activityBar(context: vscode.ExtensionContext) 
{
    const hasSFMLFiles = await checkSFMLFiles();
    const treeDataProvider = new SFMLTreeDataProvider(context, 'https://api.github.com/repos/TeamDman/SuperFactoryManager/contents/src/main/resources/assets/sfm/template_programs');
    const treeDataProvider2 = new SFMLTreeDataProvider(context, 'https://api.github.com/repos/TeamDman/SuperFactoryManager/contents/examples');
    const treeDataProvider3 = new SFMLTreeDataProvider(context);
    //If we dont have some .sfm or .sfml, we dont want to see the activity bar
    //Only when the extension activates, like some other extensions do (java extension or antlr one)
    //Dont ask why there 2 openFiles
    if(hasSFMLFiles) 
    {
        const view = vscode.window.createTreeView('examplesGames', {
            treeDataProvider: treeDataProvider
        });
        const view2 = vscode.window.createTreeView('examplegithub', {
            treeDataProvider: treeDataProvider2
        });
        const viewExternal = vscode.window.createTreeView('examplesOthers',{
            treeDataProvider: treeDataProvider3
        });
        context.subscriptions.push(view);
        context.subscriptions.push(view2);
        context.subscriptions.push(viewExternal);
    }
    else
    {
        vscode.commands.executeCommand("setContext", "sfml.isActivated", false);
    }
    
    context.subscriptions.push(getOpenCommand(tempFiles));
}

/**
 * Checks on the working directory if we have any .sfm or .sfml file on any folder
 * WIP, add more folders to the exception
 * @returns A Promise<boolean>
 */
export async function checkSFMLFiles(): Promise<boolean> 
{
    const activeEditor = vscode.window.activeTextEditor;
    if(activeEditor)
    {
        const currentFile = activeEditor.document;
        if(currentFile.languageId === 'sfml' || currentFile.languageId === 'sfm')
        {
            return true;
        }
    }
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if(workspaceFolders) //Sometimes we dont have anything on a workspace, so undefined and nothing
    {
        const files = await vscode.workspace.findFiles("**/*.{sfml,sfm}", "**/node_modules/*", 1);
        return files.length > 0;
    }
    return false;
}

//Call the os to delete the files we downloaded earlier, we dont want them
export function deleteTempFiles()
{
    tempFiles.forEach((filePath) => {
        try 
        {
            if (fs.existsSync(filePath)) 
            {
                fs.unlinkSync(filePath);
            }
        }
        catch (error) 
        {
            vscode.window.showErrorMessage(`Error deleting temporal files from the temporal directory: ${error}`);
        }
    });
    tempFiles.clear();
}