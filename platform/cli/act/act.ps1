# Action loop
while ($true) {
  # Prompt user to select an action
  $action = Get-ChildItem -Path actions `
    | Select-Object -ExpandProperty name `
    | Sort-Object -Descending `
    | fzf --prompt "Action: " --header "Select an action to run"
  if ([string]::IsNullOrWhiteSpace($action)) {
    break
  }

  # Run the selected action
  . ".\actions\$action"
  
  # Leave the action display on the screen for a moment
  # (the action loop clears it with fzf)
  pause
}
