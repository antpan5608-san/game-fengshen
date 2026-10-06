"""Small, read-only project retrieval and a deadline-aware adapter to the bridge."""
from __future__ import annotations

import ast
import contextvars
import json
import re
import time
from pathlib import Path
from urllib.request import ProxyHandler, Request, build_opener

from bridge import DRAFT_SYSTEM, LocalUnavailable, SYSTEM, Workspace, clean, sha

DOCUMENTS = ('AGENTS.md', 'docs/current-task.md', 'docs/delivery-status.md',
             'docs/local-ai-context.md')
DEADLINE = contextvars.ContextVar('local_ai_deadline', default=None)
BLOCKED = {'.secrets', 'secrets', 'venv', 'private', 'backups', '.migration-backups'}


def read_source(path: Path) -> str:
    if path.stat().st_size > 2 * 1024**2:
        raise ValueError('Source exceeds 2 MiB; choose a smaller source')
    value = path.read_text(encoding='utf-8-sig').replace('\r\n', '\n')
    if '\0' in value:
        raise ValueError('Binary source is not eligible')
    return clean(value)


def spans(text: str, suffix: str):
    """Return bounded declarations; offsets are original one-based line numbers."""
    if suffix == '.py':
        try:
            tree = ast.parse(text)
            return [(node.lineno, node.end_lineno) for node in ast.walk(tree)
                    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))]
        except SyntaxError:
            return []
    # Mask strings/comments but preserve newlines, so braces in literals don't truncate functions.
    masked = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*',
                    lambda m: ''.join('\n' if c == '\n' else ' ' for c in m[0]), text)
    matches = list(re.finditer(r'(?m)^\s*(?:(?:private|public|internal|protected|override|suspend|inline|tailrec|open|final|data)\s+)*(?:fun|func|class|object)\b[^\n]*', masked))
    result = []
    for index, match in enumerate(matches):
        start = masked.count('\n', 0, match.start()) + 1
        end_limit = matches[index+1].start() if index+1 < len(matches) else len(masked)
        opening = masked.find('{', match.start(), end_limit)
        if opening < 0:
            end = masked.count('\n', 0, end_limit)
        else:
            depth, end_offset = 0, None
            for pos in range(opening, len(masked)):
                depth += (masked[pos] == '{') - (masked[pos] == '}')
                if depth == 0:
                    end_offset = pos
                    break
            if end_offset is None:
                continue
            end = masked.count('\n', 0, end_offset) + 1
        result.append((start, max(start, end)))
    return result


class OptimizedWorkspace(Workspace):
    def __init__(self, *args, **kwargs):
        kwargs.setdefault('timeout', 30)
        super().__init__(*args, **kwargs)

    def eligible(self, relative):
        parts = Path(relative).parts
        return not any(p.lower() in BLOCKED for p in parts) and super().eligible(relative)

    def count(self, text):
        if not self.counter:
            path = self.state / 'tokenizer.json'
            stat = path.stat()
            identity = (stat.st_mtime_ns, stat.st_size)
            if getattr(self, '_tokenizer_file_identity', None) != identity:
                if hasattr(self, '_tokenizer'):
                    del self._tokenizer
                self._tokenizer_file_identity = identity
        return super().count(text)

    def request_parameters(self, model):
        profile = self.state / 'active-profile.json'
        if model != 'local-code:latest' or not profile.exists():
            return super().request_parameters(model)
        data = json.loads(profile.read_text(encoding='utf-8'))
        if data.get('status') not in {'baseline_verified', 'candidate_verified'} or data.get('model') != model:
            raise ValueError('Profile has no verified deployment status')
        params = data.get('parameters', {})
        if set(params) != {'temperature', 'top_p', 'presence_penalty'}:
            raise ValueError('Invalid verified profile parameters')
        if not all(type(value) in (int, float) for value in params.values()):
            raise ValueError('Profile values must be numeric')
        if not (0 <= params['temperature'] <= 2 and 0 < params['top_p'] <= 1 and 0 <= params['presence_penalty'] <= 2):
            raise ValueError('Profile values are outside supported bounds')
        return params

    def document(self, relative):
        if relative not in DOCUMENTS:
            raise ValueError('Not an authoritative project document')
        path = (self.root / relative).resolve()
        if not path.is_relative_to(self.root):
            raise ValueError('Document escapes the workspace')
        return read_source(path) if path.is_file() else ''

    def context_identity(self):
        return {name: sha(self.document(name)) for name in DOCUMENTS}

    def project_context(self):
        summary = self.document('docs/local-ai-context.md')
        if self.count(summary) > 550:
            raise ValueError('Project summary exceeds 550 tokens')
        # Fresh authority, not stale README versions or a full historical transcript.
        current = self.document('docs/current-task.md')
        status = [line for line in current.splitlines() if re.match(
            r'^(task_id|status|overall|stable_version|current_candidate_version|content_version|first_real_blocker):', line)]
        rules = ('原版事实必须引用已有证据；UNKNOWN/PROVISIONAL不能提升为VERIFIED。'
                 '复用已有逻辑；奖励只结算一次；保持旧存档兼容。Codex负责阅读完整AGENTS和交付记录。')
        text = summary + '\n当前任务事实：\n' + '\n'.join(status) + '\n' + rules
        if self.count(text) > 900:
            raise ValueError('Current context exceeds 900 tokens; narrow the task')
        return text

    def search(self, query: str, limit: int = 5):
        if not isinstance(query, str) or not query.strip() or len(query) > 256:
            raise ValueError('Provide a nonempty query of at most 256 characters')
        if not isinstance(limit, int) or not 1 <= limit <= 10:
            raise ValueError('Limit must be between 1 and 10')
        terms = list(dict.fromkeys(re.findall(r'[\w.-]+', query.lower())))
        rows = []
        for relative, source in self.inventory().items():
            text = source['text']
            if text is None:
                continue
            lines = text.splitlines()
            hits = [(i, sum(1 for term in terms if term in line.lower()))
                    for i, line in enumerate(lines, 1)]
            hits = [(line, score) for line, score in hits if score]
            filename_score = sum(3 for term in terms if term in relative.lower())
            if not hits and not filename_score:
                continue
            declarations = spans(text, Path(relative).suffix)
            seeds = sorted(hits, key=lambda row: (-row[1], row[0]))[:3] or [(1, 0)]
            seen = set()
            for line, score in seeds:
                covering = [(a, b) for a, b in declarations if a <= line <= b]
                start, end = min(covering, key=lambda v: v[1]-v[0]) if covering else (max(1, line-6), min(len(lines), line+8))
                complete = bool(covering)
                if end-start > 100:
                    start, end, complete = max(start, line-6), min(end, line+14), False
                if (start, end) in seen:
                    continue
                seen.add((start, end))
                snippet = clean('\n'.join(f'{i}: {lines[i-1]}' for i in range(start, end+1)))
                if self.count(snippet) > 1600:
                    snippet = clean('\n'.join(f'{i}: {lines[i-1]}' for i in range(max(1, line-2), min(len(lines), line+3)+1)))
                    start, end, complete = max(1, line-2), min(len(lines), line+3), False
                    if self.count(snippet) > 1600:
                        continue
                rows.append(dict(path=relative, start_line=start, end_line=end, complete_declaration=complete,
                                 sha256=source['hash'], score=filename_score+score, text=snippet))
        rows.sort(key=lambda r: (-r['score'], r['path'], r['start_line']))
        return dict(query=query, matches=rows[:limit], advisory_only=True,
                    context_identity=self.context_identity())

    def model_identity(self, model):
        if self.transport:
            return {'id': model, 'transport': 'injected'}
        end = DEADLINE.get()
        remaining = max(.05, end-time.monotonic()) if end else self.timeout
        req = Request('http://192.168.1.20:8000/v1/models', headers=self.auth_headers())
        try:
            with build_opener(ProxyHandler({})).open(req, timeout=min(5, remaining)) as response:
                rows = json.load(response)['data']
            row = next(r for r in rows if r['id'] == model)
            identity = {k: row[k] for k in ('id', 'created', 'digest') if k in row}
            registry = self.state / 'model-digests.json'
            if registry.exists():
                record = json.loads(registry.read_text(encoding='utf-8')).get(model)
                if record and record.get('created') == row.get('created'):
                    identity['digest'] = record['digest']
                elif record:
                    raise LocalUnavailable('model_manifest_stale')
            return identity
        except (OSError, ValueError, KeyError, StopIteration) as exc:
            raise LocalUnavailable('model_identity_unavailable') from None

    def auth_headers(self):
        import os
        key = os.environ.get('LOCAL_AI_API_KEY')
        if not key:
            path = Path(os.environ.get('LOCAL_AI_CREDENTIALS_FILE', str(Path.home() / 'Documents/ChatGPT/local-llm/.secrets/access.json')))
            try:
                key = json.loads(path.read_text(encoding='utf-8'))['api_key']
            except (OSError, ValueError, KeyError):
                raise LocalUnavailable('credentials_unavailable') from None
        return {'Authorization': 'Bearer '+key, 'Content-Type': 'application/json'}

    def ask(self, prompt, model='local-code:latest', deadline=None, system=SYSTEM):
        end = min(deadline, time.monotonic()+self.timeout) if deadline is not None else time.monotonic()+self.timeout
        token = DEADLINE.set(end)
        try:
            identity = self.model_identity(model)
            tokenizer = self.state / 'tokenizer-receipt.json'
            manifest = dict(model=identity, context=self.context_identity(), prompt_version=1,
                            tokenizer=sha(tokenizer.read_bytes()) if tokenizer.exists() else 'injected',
                            parameters=self.request_parameters(model))
            suffix = '\n上下文与模型快照：'+sha(json.dumps(manifest, sort_keys=True))
            result = super().ask(prompt+suffix, model, deadline=end, system=system)
            if time.monotonic() > DEADLINE.get():
                raise LocalUnavailable('task_deadline_exceeded')
            return dict(result, identity=manifest)
        finally:
            DEADLINE.reset(token)

    def draft(self, task, paths, acceptance, model='local-code:latest', ranges=None):
        if ranges is not None and not isinstance(ranges, list):
            raise ValueError('ranges must be a list of path/start_line/end_line objects')
        selected = list(ranges or [])
        ranged_paths = {r.get('path') for r in selected if isinstance(r, dict)}
        selected += [{'path': p} for p in paths if p not in ranged_paths]
        source, receipts = [], []
        for selection in selected:
            if not isinstance(selection, dict) or 'path' not in selection:
                raise ValueError('Each range requires a path')
            relative = selection['path']
            text = read_source(self.safe_path(relative))
            lines = text.splitlines()
            start, end = selection.get('start_line', 1), selection.get('end_line', len(lines))
            if not isinstance(start, int) or not isinstance(end, int) or not 1 <= start <= end <= len(lines):
                raise ValueError('Line range is outside the selected source')
            source.append(f'\n文件 {relative}:{start}-{end}\n'+'\n'.join(f'{i}: {lines[i-1]}' for i in range(start, end+1)))
            receipts.append(dict(path=relative, start_line=start, end_line=end, sha256=sha(text)))
        prefix = f'{self.project_context()}\n任务：{clean(task)}\n验收：{clean(acceptance)}\n只返回任务要求的代码块，严格使用任务指定语言。生成测试时禁止重写被测函数；信息不足请列缺口。\n'
        prompt = prefix+'\n'.join(source)
        if self.count(DRAFT_SYSTEM+prompt) > 2600:
            raise ValueError('Draft context exceeds 2600 tokens; select complete smaller functions')
        return dict(advisory_only=True, model=model, sources=receipts, **self.ask(prompt, model, system=DRAFT_SYSTEM))
