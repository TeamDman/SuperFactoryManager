import * as vscode from 'vscode';
import axios from 'axios';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';

export class SFMLTreeDataProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
    private _onDidChangeTreeData = new vscode.EventEmitter<vscode.TreeItem | undefined>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;
    private context: vscode.ExtensionContext;

    private githubUrls: string[] = [];
    private localPaths: string[] = [];
    private filesData = new Map<string, any[]>();

    private showFilesFirst: boolean;
    private enableActivityBar: boolean;
    private repositoryUrl: string = "";

    constructor(context: vscode.ExtensionContext, githubApiUrl?: string) {
        
        this.showFilesFirst = vscode.workspace.getConfiguration('sfml').get('filesOrder', false);
        this.enableActivityBar = vscode.workspace.getConfiguration('sfml').get('enableActivityBar', true);
        vscode.commands.executeCommand("setContext", "sfml.isActivated", this.enableActivityBar);
        
        this.context = context;
        this.setupConfigListeners();

        if(githubApiUrl)
        {
            this.repositoryUrl = githubApiUrl;
        }
        
        if(this.enableActivityBar)
        {
            this.loadSources();
        }
        
    }

    private setupConfigListeners()
    {
        //Listener when config changes
        vscode.workspace.onDidChangeConfiguration(event => {
            
            if(event.affectsConfiguration('sfml.filesOrder'))
            {
                this.showFilesFirst = vscode.workspace.getConfiguration('sfml').get('filesOrder', false);
            }

            if(event.affectsConfiguration('sfml.enableActivityBar'))
            {
                this.enableActivityBar = vscode.workspace.getConfiguration('sfml').get('enableActivityBar', true);
                vscode.commands.executeCommand("setContext", "sfml.isActivated", this.enableActivityBar);
                if(this.enableActivityBar)
                {
                    this.loadSources()
                }
            }

            // Configuraciones específicas del modo
            if(event.affectsConfiguration('sfml.externalURLs'))
            {
                this.loadSources()
                
            }

            this.refresh();
        });
    }

    private refresh()
    {
        this._onDidChangeTreeData.fire(undefined);
    }

    private loadURLfromSettings() 
    {
        const config = vscode.workspace.getConfiguration('sfml');
        this.githubUrls = [];
        this.localPaths = [];
        
        // Get the externalURL setting value
        const settingValue = config.get<string>('externalURLs', '');
        
        // Parse URLs and local paths from the setting value
        const sources = settingValue.split(',').map(source => source.trim().replace(/^'|'$/g, ''));
    
        const transformedUrls = sources.map(source => {
            if(source.startsWith('https://github.com')) 
            {
                try 
                {
                    return getApiUrlFromGithubUrl(source);
                }
                catch(e)
                {
                    console.error(`Error converting GitHub URL: ${source}`, e);
                    return null;
                }
            }
            return source;
        }).filter(source => source !== null);
        
        // Filter GitHub URLs and local paths
        this.githubUrls = transformedUrls.filter(source =>
            source.startsWith('https://api.github.com')
        );
    
        this.localPaths = sources.filter(source => 
            source !== '' && 
            !source.startsWith('https://api.github.com') && 
            !source.startsWith('https://github.com')
        );
    
        if(this.localPaths.length !== 0 || this.githubUrls.length !== 0)
        {
            vscode.commands.executeCommand("setContext", "sfml.thereAreFiles", true);
        }
        else
        {
            vscode.commands.executeCommand("setContext", "sfml.thereAreFiles", false);
        }
    }

    private async loadSources() 
    {
        if(this.repositoryUrl.length !== 0)
        {
            this.filesData.set(this.repositoryUrl, await this.loadRepoContents(this.repositoryUrl));
        }
        else
        {
            this.loadURLfromSettings();
            for(const url of this.githubUrls)
            {
                try
                {
                    this.filesData.set(url, await this.loadRepoContents(url));
                }
                catch(error)
                {
                    vscode.window.showErrorMessage(`Error fetching from GitHub: ${error}`);
                }
            }
        
            for(const path of this.localPaths) 
            {
                this.filesData.set(path, []); //Initialize empty array for local paths
            }
        }
        this.refresh();
    }

    //Download the structure from github
    async loadRepoContents(url : string) 
    {
        try 
        {
            const array: any[] = [];
            const response = await axios.get(url);

            response.data.forEach((element: { type: string; name: any; url: any; download_url: any; }
            ) => {
                if(element.type === 'dir')
                {
                    array.push({
                        name : element.name,
                        type : element.type,
                        url  : element.url
                    })
                }
                else
                {
                    array.push({
                        name : element.name,
                        type : element.type,
                        url  : element.download_url
                    })
                }
            })
            return array;
        } 
        catch(error) 
        {
            vscode.window.showErrorMessage(`Error fetching examples from github: ${error}`);
            return [];
        }
    }

    /**
     * Get the structure of a folder
     * @param path Local path to a folder
     */
    private async loadLocalFiles(path: string) 
    {
        try 
        {
            const files = await vscode.workspace.fs.readDirectory(vscode.Uri.file(path));
            const localFiles = files.map(([name, type]) => ({
                name,
                type: type === vscode.FileType.Directory ? 'dir' : 'file',
                url: path + '/' + name,
            }))
            .filter(file => file.type === 'dir' || /\.(sfm|sfml)$/.test(file.name));
            this.filesData.set(path, localFiles);
        } 
        catch(error) 
        {
            vscode.window.showErrorMessage(`Error reading local files: ${error}`);
        }
    }

    getTreeItem(element: vscode.TreeItem): vscode.TreeItem
    {
        return element;
    }
    
    //Basically the same as SFMLTreeDataProvider but for files and separete different repos and local folders
    async getChildren(element?: vscode.TreeItem): Promise<vscode.TreeItem[]>
    {
        if(!element)
        {
            // Root items
            if(this.repositoryUrl)
            {
                // Modo GitHub único
                const rootData = this.filesData.get(this.repositoryUrl) || [];
                return this.processItems(rootData);
            }
            
            // Modo mixto (GitHub + local)
            return [
                ...this.githubUrls.map(url => this.createSourceItem(url, 'github')),
                ...this.localPaths.map(path => this.createSourceItem(path, 'local'))
            ];
        }

        // Child items
        const source = element.id as string;
        let items: any[] = [];

        if(source.startsWith('https://api.github.com'))
        {
            // GitHub folder
            if(!this.filesData.has(source))
            {
                const contents = await this.loadRepoContents(source);
                this.filesData.set(source, contents);
            }
            items = this.filesData.get(source) || [];
        }
        else
        {
            // Local folder
            await this.loadLocalFiles(source);
            items = this.filesData.get(source) || [];
        }

        return this.processItems(items);
    }

    private createSourceItem(path: string, type: 'github' | 'local'): vscode.TreeItem
    {
        return this.createTreeItem({
            name: type === 'github' ? extractGithubUsername(path) : path.split('/').pop() || '',
            type: 'dir',
            url: path
        });
    }

    private processItems(items: any[]): vscode.TreeItem[] {
        const validExtensions = /\.(sfm|sfml)$/;
        
        const filtered = items.filter(item => 
            item.type === 'dir' || 
            (item.type === 'file' && validExtensions.test(item.name))
        );

        const [folders, files] = this.partitionItems(filtered);
        const sorted = this.showFilesFirst ? [...files, ...folders] : [...folders, ...files];

        return sorted.map(item => this.createTreeItem({
            name: item.name,
            type: item.type,
            url: item.url || item.url
        }));
    }

    private partitionItems(items: any[]): [any[], any[]] {
        const folders: any[] = [];
        const files: any[] = [];
        
        items.forEach(item => {
            item.type === 'dir' ? folders.push(item) : files.push(item);
        });
        
        return [folders, files];
    }
    
    //The same as the sfml tree data provider but this time adding where the files are located
    private createTreeItem(file: {name: string, type: string, url: string}): vscode.TreeItem 
    {
        const iconPath = loadIconPaths(this.context)
        const treeItem = new vscode.TreeItem(file.name);
        if(file.type === 'dir') 
        {
            treeItem.collapsibleState = vscode.TreeItemCollapsibleState.Collapsed;
            treeItem.iconPath = iconPath.folder;
        } 
        else 
        {
            treeItem.collapsibleState = vscode.TreeItemCollapsibleState.None;
            treeItem.iconPath = iconPath.file;
        }
        treeItem.id = file.url; //Needed for the local files
        treeItem.command = {
            command: 'sfml.openFile',
            title: 'Open File',
            arguments: [file]
        };
    
        return treeItem;
    }
}

/**
 * Get the icon selected from the setting and according to that, it will change the icons
 * @param context Vscode extension
 * @returns A array with 2 thing, one for icon path and another for folder icon
 */
export function loadIconPaths(context: vscode.ExtensionContext) 
{
    const iconConfig = vscode.workspace.getConfiguration('sfml');
    const fileIcon = iconConfig.get('changeFileIconsOnActivityBar', 'exp');
    const folderIcon = iconConfig.get('changeFolderIconsOnActivityBar', 'tool');
    
    const iconMap: { [key: string]: string } = {
        'Disk': 'disk.png',
        'Controller': 'controller.png',
        'Label Gun': 'label gun.png',
        'Experience Goop': 'experience goop.png',
        'Experience Shard': 'experience shard.png',
        'Tool Network': 'tool.png',
        'Printing Form': 'printing press.png'
    };
    
    return {
        file: {
            light: vscode.Uri.file(path.join(context.extensionPath, 'media', iconMap[fileIcon] || 'experience goop.png')),
            dark: vscode.Uri.file(path.join(context.extensionPath, 'media', iconMap[fileIcon] || 'experience goop.png'))
        },
        folder: {
            light: vscode.Uri.file(path.join(context.extensionPath, 'media', iconMap[folderIcon] || 'tool.png')),
            dark: vscode.Uri.file(path.join(context.extensionPath, 'media', iconMap[folderIcon] || 'tool.png'))
        }
    };
}

/**
 * Handle everything related to downloading files and folders on github and local
 * Local files and folder will load the structure as github one, to try reduce the usage
 * GitHub files and folder will load only the structure to reduce internet usage. Should be small
 * if you do not open a file (all Teamy files have cost 1MB)
 * url on files doesnt makes sense but it was to reuse the code
 * @param tempFiles Map in which the downloaded files are located, usually on tmp dir
 * @returns Open the file on a new windows or shows an error
 */
export function getOpenCommand(tempFiles: Map<string, string>): vscode.Disposable {
    return vscode.commands.registerCommand('sfml.openFile', async (file) => {
        if (file.type === 'dir') {
            //Not worth it, due to the 2 different stuff to get files
            return;
        }

        // Check if the file is a local file or online and there is a url
        if (file.url && !file.url.includes('https://')) {
            // Handle local file
            try {
                const document = await vscode.workspace.openTextDocument(file.url);
                vscode.window.showTextDocument(document);
            }
            catch (error) {
                vscode.window.showErrorMessage(`Error while opening local file: ${error}`);
            }
        }
        else {
            // Handle online file
            const tempFilePath = path.join(os.tmpdir(), path.basename(file.name));
            if(tempFiles.has(tempFilePath)) {
                // If file already exists in temp, open it
                const fileUri = vscode.Uri.file(tempFilePath);
                const document = await vscode.workspace.openTextDocument(fileUri);
                vscode.window.showTextDocument(document);    
            }
            else //Get the file from github and its content
            {
                try {
                    const response = await axios.get(file.url, { responseType: 'arraybuffer' });
                    fs.writeFileSync(tempFilePath, response.data);
                    tempFiles.set(tempFilePath, tempFilePath);

                    const fileUri = vscode.Uri.file(tempFilePath);
                    const document = await vscode.workspace.openTextDocument(fileUri);
                    vscode.window.showTextDocument(document);
                }
                catch (error) {
                    vscode.window.showErrorMessage(`Error while opening file: ${error}`);
                }
            }
        }
    });
}

/**
 * @param url API GitHub url, it will get the user name
 * For example, takes this url: https://api.github.com/repos/TeamDman/SuperFactoryManager/contents/examples
 * In this case it will return TeamDman, which is the regex pattern
 * @returns username from the url
 */
export function extractGithubUsername(url: string): string 
{
    const match = url.match(/api\.github\.com\/repos\/([^\/]+)\//);
    return match ? match[1] : 'unknown'; // Devuelve 'unknown' si no se encuentra el nombre de usuario
}

/**
 * Transform the url from https://github.com to a api one, including the branch
 * @param githubUrl GitHub url, not the api one
 * @returns Api url from github
 */
export function getApiUrlFromGithubUrl(githubUrl: string): string 
{
    // Expresión regular para extraer la información de la URL de GitHub para archivos o directorios
    const treeRegex = /https:\/\/github\.com\/([^\/]+)\/([^\/]+)\/tree\/([^\/]+)\/(.+)/;
    const repoRegex = /https:\/\/github\.com\/([^\/]+)\/([^\/]+)/;

    let match = githubUrl.match(treeRegex);
    if(match) 
    {
        const [, owner, repo, branch, path] = match;
        const apiUrl = `https://api.github.com/repos/${owner}/${repo}/contents/${path}?ref=${branch}`;
        return apiUrl;
    }

    match = githubUrl.match(repoRegex);
    if(match) 
    {
        const [, owner, repo] = match;
        const apiUrl = `https://api.github.com/repos/${owner}/${repo}/contents/`;
        return apiUrl;
    }
    console.log("Url no valid" + githubUrl)
    return "";
}
