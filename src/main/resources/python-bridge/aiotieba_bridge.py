#!/usr/bin/env python3
import sys
import json
import asyncio
import traceback

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')
sys.stdin.reconfigure(encoding='utf-8', errors='replace')

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

try:
    import aiotieba
except ImportError:
    print(json.dumps({"error": "aiotieba not installed. Run: pip install aiotieba"}))
    sys.exit(1)


def fmt_time(ts: int) -> str:
    if not ts:
        return ""
    import datetime
    return datetime.datetime.fromtimestamp(ts).strftime("%m-%d %H:%M")


def rebuild_text(contents) -> str:
    """按内容碎片顺序重建文本,把表情还原为 [描述] 占位符(如 [泪]).

    aiotieba 的 contents.text 只拼接纯文本碎片,表情碎片会被丢弃。
    """
    if contents is None:
        return ""
    parts = []
    for frag in getattr(contents, "objs", []):
        if getattr(frag, "desc", None) is not None and getattr(frag, "id", None) is not None:
            parts.append(f"[{frag.desc or frag.id}]")
        else:
            text = getattr(frag, "text", None)
            if isinstance(text, str) and text:
                parts.append(text)
    return "".join(parts)


async def handle_get_threads(forum: str, page: int = 1, is_good: bool = False):
    async with aiotieba.Client() as client:
        threads_result = await client.get_threads(forum, page, is_good=is_good)
    threads = []
    for t in threads_result.objs:
        user = t.user
        threads.append({
            "tid": t.tid,
            "title": t.title,
            "text": (rebuild_text(getattr(t, "contents", None)) or t.text)[:200],
            "author": (user.nick_name or user.user_name) if user else "",
            "replyNum": t.reply_num,
            "viewNum": t.view_num,
            "lastTime": fmt_time(t.last_time),
            "isGood": t.is_good,
            "isTop": t.is_top,
        })
    return {
        "threads": threads,
        "totalPage": threads_result.page.total_page if threads_result.page else 0,
        "currentPage": page,
        "hasMore": threads_result.has_more,
    }


async def handle_get_posts(tid: int, page: int = 1, only_op: bool = False):
    async with aiotieba.Client() as client:
        posts_result = await client.get_posts(tid, page)
    posts = []
    for p in posts_result.objs:
        user = p.user
        imgs = []
        if p.contents and p.contents.imgs:
            for img in p.contents.imgs:
                if img.big_src:
                    imgs.append(img.big_src)
                elif img.src:
                    imgs.append(img.src)
        posts.append({
            "floor": p.floor,
            "pid": p.pid,
            "author": (user.nick_name or user.user_name) if user else "",
            "text": rebuild_text(p.contents) or p.text,
            "imgs": imgs,
            "timestamp": fmt_time(p.create_time),
            "isOp": p.is_thread_author,
            "replyNum": p.reply_num,
        })
    if only_op:
        posts = [p for p in posts if p["isOp"]]
    return {
        "posts": posts,
        "totalPage": posts_result.page.total_page if posts_result.page else 0,
        "currentPage": page,
        "hasMore": posts_result.has_more,
        "threadTitle": posts_result.thread.title if posts_result.thread else "",
    }


async def handle_get_comments(tid: int, pid: int, page: int = 1):
    async with aiotieba.Client() as client:
        comments_result = await client.get_comments(tid, pid, page)
    comments = []
    for c in comments_result.objs:
        user = c.user
        comments.append({
            "author": (user.nick_name or user.user_name) if user else "",
            "text": rebuild_text(c.contents) or c.text,
            "timestamp": fmt_time(c.create_time),
            "isOp": c.is_thread_author,
            "agree": c.agree,
        })
    return {
        "comments": comments,
        "totalPage": comments_result.page.total_page if comments_result.page else 0,
        "currentPage": page,
        "hasMore": comments_result.has_more,
    }


async def handle_health():
    return {"status": "ok"}


async def process_request(request: dict) -> dict:
    action = request.get("action", "")
    try:
        if action == "get_threads":
            return await handle_get_threads(
                request.get("forum", ""), request.get("page", 1), request.get("is_good", False)
            )
        elif action == "get_posts":
            return await handle_get_posts(request.get("tid", 0), request.get("page", 1), request.get("only_op", False))
        elif action == "get_comments":
            return await handle_get_comments(request.get("tid", 0), request.get("pid", 0), request.get("page", 1))
        elif action == "health":
            return await handle_health()
        else:
            return {"error": f"Unknown action: {action}"}
    except Exception as e:
        return {"error": str(e), "traceback": traceback.format_exc()}


async def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        req_id = 0
        try:
            request = json.loads(line)
            req_id = request.get("req_id", 0)
            result = await process_request(request)
            result["req_id"] = req_id
            sys.stdout.write(json.dumps(result, ensure_ascii=False) + "\n")
            sys.stdout.flush()
        except json.JSONDecodeError as e:
            sys.stdout.write(json.dumps({"error": f"Invalid JSON: {e}", "req_id": req_id}) + "\n")
            sys.stdout.flush()
        except Exception as e:
            sys.stdout.write(json.dumps({"error": str(e), "traceback": traceback.format_exc(), "req_id": req_id}) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    asyncio.run(main())
