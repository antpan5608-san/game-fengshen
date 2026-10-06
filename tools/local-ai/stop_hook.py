"""Fast local-only receipt check; deliberately does not call the model."""
import json
import sys
from context_tools import OptimizedWorkspace

if __name__ == '__main__':
    try:
        event = json.load(sys.stdin)
        output = OptimizedWorkspace().stop(event)
    except Exception:
        output = {'continue': False, 'stopReason': 'Local review guard failed',
                  'systemMessage': '本地审查检查失败；请检查工具状态，不能声称该轮审查完成。'}
    print(json.dumps(output, ensure_ascii=True))
