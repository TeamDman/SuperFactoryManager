$discussions = gh api graphql -f query='query {
    repository(owner: "teamdman", name: "superfactorymanager") {
      discussions(first: 50) {
        nodes {
          id
          title
          url
        }
      }
    }
  }
  ' `
| ConvertFrom-Json
$discussions = $discussions.data.repository.discussions.nodes

$discussion_id = $discussions `
| Format-Table `
| Out-String `
| ForEach-Object { $_ -split "`n" } `
| Select-Object -Skip 1 -SkipLast 2 `
| fzf --header-lines 2 --height ~100%
$discussion_id = $discussion_id -split '\s+' | Select-Object -First 1

# https://docs.github.com/en/graphql/guides/using-the-graphql-api-for-discussions
$discussion = gh api graphql -F query='
query($discussionId: ID!, $after: String) {
  node(id: $discussionId) {
    ... on Discussion {
        body
      comments(first: 50, after: $after) {
        edges {
          node {
            body
            author {
              login
            }
            createdAt
          }
        }
        pageInfo {
          hasNextPage
          endCursor
        }
      }
    }
  }
}' -F discussionId="$discussion_id" `
| ConvertFrom-Json
$content = $discussion.data.node.body + "`n`n" + $discussion.data.node.comments.edges.node.body
$content | Set-Clipboard
Write-Host "Content:"
Write-Host $content
Write-Host "Set to clipboard"