#!/usr/bin/env python3
"""Validate the reviewed wiki sources and optionally export them into a wiki checkout."""
from argparse import ArgumentParser
from pathlib import Path
from urllib.parse import unquote
import re

SOURCE = Path(__file__).resolve().parent / "wiki"
LINK = re.compile(r"\]\(([^)]+)\)")


def wiki_links(text):
    """Repository pages use .md links; GitHub wiki routes use page names without .md."""
    def replace(match):
        target = match.group(1)
        if "://" in target or target.startswith(("#", "mailto:")):
            return match.group(0)
        page, separator, anchor = target.partition("#")
        if page.endswith(".md"):
            page = page[:-3]
        return "](" + page + (separator + anchor if separator else "") + ")"
    return LINK.sub(replace, text)


def validate(source=SOURCE):
    pages = sorted(source.glob("*.md"))
    if not pages or not (source / "Home.md").is_file() or not (source / "_Sidebar.md").is_file():
        raise ValueError("Wiki sources need Home.md and _Sidebar.md")
    for page in pages:
        for target in LINK.findall(page.read_text(encoding="utf-8")):
            if "://" in target or target.startswith(("#", "mailto:")):
                continue
            destination = unquote(target.split("#", 1)[0])
            if destination and not (page.parent / destination).is_file():
                raise ValueError(f"Missing target in {page.name}: {target}")
    return pages


def export(destination, source=SOURCE):
    pages = validate(source)
    destination = destination.resolve()
    if destination == source.resolve() or source.resolve() in destination.parents:
        raise ValueError("Export into a separate wiki checkout, not into the source pages")
    destination.mkdir(parents=True, exist_ok=True)
    for page in pages:
        (destination / page.name).write_text(wiki_links(page.read_text(encoding="utf-8")), encoding="utf-8")
    return len(pages)


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, help="Existing wiki Git checkout; local writes only")
    args = parser.parse_args()
    if args.destination:
        print(f"Exported {export(args.destination)} reviewed pages; no Git commit or push was performed.")
    else:
        print(f"Validated {len(validate())} wiki source pages.")


if __name__ == "__main__":
    main()
