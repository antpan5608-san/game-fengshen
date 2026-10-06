import argparse
import json
from context_tools import OptimizedWorkspace

parser = argparse.ArgumentParser()
parser.add_argument('command', choices=['init', 'status', 'review', 'decide'])
parser.add_argument('--task', default='LOCAL-AI-INTEGRATION')
parser.add_argument('--goal', default='Review the local integration changes; no game behavior changes.')
parser.add_argument('--snapshot')
parser.add_argument('--checks', nargs='*')
parser.add_argument('--decisions', nargs='*', default=[])
parser.add_argument('--mode', default='reviewed')
parser.add_argument('--reason', default='')
args = parser.parse_args()
w = OptimizedWorkspace()
if args.command == 'init':
    result = w.initialize()
elif args.command == 'status':
    result = w.status()
elif args.command == 'review':
    result = w.review(args.task, args.goal)
else:
    result = w.decide(args.snapshot, args.checks, args.decisions, args.mode, args.reason)
print(json.dumps(result, ensure_ascii=True, indent=2))
