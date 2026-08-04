#!/usr/bin/env python3
"""
md_to_pdf.py — Convert a Markdown (.md) file into a clean, readable PDF.

Install dependencies (once):
    pip3 install markdown xhtml2pdf

Usage:
    python3 md_to_pdf.py notes.md
    python3 md_to_pdf.py notes.md -o notes_output.pdf
"""

import argparse
import sys
from pathlib import Path

import markdown
from xhtml2pdf import pisa

# Minimal, readable CSS styling for the generated PDF
PDF_CSS = """
@page {
    size: letter;
    margin: 2cm;
}
body {
    font-family: Helvetica, Arial, sans-serif;
    font-size: 11pt;
    line-height: 1.5;
    color: #222222;
}
h1 {
    font-size: 22pt;
    margin-top: 0;
    margin-bottom: 10px;
    border-bottom: 1px solid #cccccc;
    padding-bottom: 6px;
}
h2 {
    font-size: 16pt;
    margin-top: 20px;
    margin-bottom: 8px;
    color: #111111;
}
h3 {
    font-size: 13pt;
    margin-top: 16px;
    margin-bottom: 6px;
}
p {
    margin: 6px 0;
}
a {
    color: #1a5fb4;
    text-decoration: none;
}
code {
    font-family: Courier, monospace;
    background-color: #f2f2f2;
    padding: 1px 4px;
    font-size: 10pt;
}
pre {
    background-color: #f2f2f2;
    padding: 10px;
    font-size: 9.5pt;
    font-family: Courier, monospace;
    white-space: pre-wrap;
    border: 0.5px solid #dddddd;
}
blockquote {
    margin: 8px 0;
    padding-left: 12px;
    border-left: 3px solid #cccccc;
    color: #555555;
}
table {
    border-collapse: collapse;
    width: 100%;
    margin: 10px 0;
}
th, td {
    border: 0.5px solid #bbbbbb;
    padding: 6px 8px;
    font-size: 10pt;
    text-align: left;
}
th {
    background-color: #f2f2f2;
}
ul, ol {
    margin: 6px 0;
    padding-left: 22px;
}
hr {
    border: none;
    border-top: 1px solid #cccccc;
    margin: 16px 0;
}
"""


def convert_md_to_pdf(md_path: Path, pdf_path: Path) -> None:
    md_text = md_path.read_text(encoding="utf-8")

    html_body = markdown.markdown(
        md_text,
        extensions=["extra", "tables", "fenced_code", "sane_lists", "toc"],
    )

    full_html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>{PDF_CSS}</style>
</head>
<body>
{html_body}
</body>
</html>"""

    with open(pdf_path, "wb") as f:
        result = pisa.CreatePDF(full_html, dest=f)

    if result.err:
        raise RuntimeError(f"Failed to create PDF ({result.err} errors).")


def main():
    parser = argparse.ArgumentParser(description="Convert a Markdown file to a styled PDF.")
    parser.add_argument("input", type=str, help="Path to the input .md file")
    parser.add_argument(
        "-o", "--output", type=str, default=None,
        help="Path to the output .pdf file (defaults to same name as input)"
    )
    args = parser.parse_args()

    md_path = Path(args.input).expanduser().resolve()
    if not md_path.exists():
        sys.exit(f"Error: file not found: {md_path}")

    pdf_path = Path(args.output).expanduser().resolve() if args.output else md_path.with_suffix(".pdf")

    convert_md_to_pdf(md_path, pdf_path)
    print(f"✅ PDF created: {pdf_path}")


if __name__ == "__main__":
    main()
