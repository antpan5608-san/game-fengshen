"""STDIO tools. Protocol output is stdout; diagnostics must remain on stderr."""
from mcp.server.mcpserver import MCPServer
from context_tools import OptimizedWorkspace

mcp = MCPServer('fengshenLocalAI')
workspace = OptimizedWorkspace()


@mcp.tool()
def draft_code(task: str, paths: list[str], acceptance: str, model: str = 'local-code:latest', ranges: list[dict] | None = None) -> dict:
    """Draft one small function/test using explicitly selected public source paths. Never edits source."""
    return workspace.draft(task, paths, acceptance, model, ranges)


@mcp.tool()
def search_project_code(query: str, limit: int = 5) -> dict:
    """Read-only lexical retrieval with original line ranges/hashes. No embeddings or inference."""
    return workspace.search(query, limit)


@mcp.tool()
def search_project(query: str, limit: int = 5) -> dict:
    """Alias for search_project_code; return bounded source declarations and hashes."""
    return workspace.search(query, limit)


@mcp.tool()
def review_changes(task_id: str, goal: str, model: str = 'local-code:latest') -> dict:
    """Review every eligible code change since the last accepted round, including committed/new/deleted files."""
    return workspace.review(task_id, goal, model)


@mcp.tool()
def record_review_decision(snapshot: str, checks: list[str], decisions: list[str], mode: str = 'reviewed', reason: str = '') -> dict:
    """After actual Codex review/testing, accept a current snapshot or acknowledge explicit manual fallback."""
    return workspace.decide(snapshot, checks, decisions, mode, reason)


@mcp.tool()
def review_status() -> dict:
    """Return current code hash and pending coverage; a receipt never grants publication permission."""
    return workspace.status()


if __name__ == '__main__':
    mcp.run(transport='stdio')
