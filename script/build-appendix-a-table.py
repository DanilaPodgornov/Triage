#!/usr/bin/env python3
"""
Replace Appendix A file list paragraphs with a 3-column Word table in VKR DOCX.

Usage:
    python script/build-appendix-a-table.py
    python script/build-appendix-a-table.py --dry-run
    python script/build-appendix-a-table.py --docx path/to/doc.docx --backup path/to/backup.docx
"""

from __future__ import annotations

import argparse
import re
import shutil
import zipfile
from dataclasses import dataclass
from pathlib import Path

from lxml import etree

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
W = f"{{{W_NS}}}"

APPENDIX_TITLE = "Приложение А"
FILE_RE = re.compile(r"^Файл «(.+)» \((.+)\)$")
TABLE_HEADERS = ("Номер", "Имя файла", "Размер")
COL_WIDTHS = (900, 6200, 1800)


@dataclass(frozen=True)
class FileEntry:
    number: int
    path: str
    size: str


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def paragraph_text(element: etree._Element) -> str:
    return "".join(element.xpath(".//w:t/text()", namespaces={"w": W_NS})).strip()


def paragraph_style(element: etree._Element) -> str | None:
    values = element.xpath("./w:pPr/w:pStyle/@w:val", namespaces={"w": W_NS})
    return values[0] if values else None


def load_document(docx_path: Path) -> etree._Element:
    with zipfile.ZipFile(docx_path, "r") as zin:
        return etree.fromstring(zin.read("word/document.xml"))


def write_document_xml(docx_path: Path, document: etree._Element) -> None:
    updated_xml = etree.tostring(
        document,
        xml_declaration=True,
        encoding="UTF-8",
        standalone=True,
    )
    temp_path = docx_path.with_suffix(".tmp.docx")
    with zipfile.ZipFile(docx_path, "r") as zin, zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename == "word/document.xml":
                zout.writestr(info, updated_xml)
            else:
                zout.writestr(info, zin.read(info.filename))

    try:
        temp_path.replace(docx_path)
    except OSError as exc:
        alt = docx_path.with_name(f"{docx_path.stem}-table.docx")
        if alt.exists():
            alt.unlink()
        temp_path.replace(alt)
        raise RuntimeError(
            f"Could not overwrite '{docx_path}' ({exc}). Close Word and rerun, or use '{alt}'."
        ) from exc


def make_table_cell(text: str, width: int, *, header: bool = False) -> etree._Element:
    tc = etree.Element(f"{W}tc")
    tc_pr = etree.SubElement(tc, f"{W}tcPr")
    etree.SubElement(tc_pr, f"{W}tcW", {f"{W}w": str(width), f"{W}type": "dxa"})
    if header:
        borders = etree.SubElement(tc_pr, f"{W}tcBorders")
        etree.SubElement(
            borders,
            f"{W}bottom",
            {f"{W}val": "double", f"{W}sz": "4", f"{W}space": "0", f"{W}color": "auto"},
        )

    paragraph = etree.SubElement(tc, f"{W}p")
    p_pr = etree.SubElement(paragraph, f"{W}pPr")
    etree.SubElement(p_pr, f"{W}pStyle", {f"{W}val": "Tabletext"})
    etree.SubElement(p_pr, f"{W}ind", {f"{W}firstLine": "0"})

    if text:
        run = etree.SubElement(paragraph, f"{W}r")
        text_el = etree.SubElement(run, f"{W}t")
        text_el.text = text
        if text.startswith(" ") or text.endswith(" "):
            text_el.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")

    return tc


def make_table_row(cells: list[tuple[str, int]], *, header: bool = False) -> etree._Element:
    row = etree.Element(f"{W}tr")
    for text, width in cells:
        row.append(make_table_cell(text, width, header=header))
    return row


def build_file_table(entries: list[FileEntry]) -> etree._Element:
    tbl = etree.Element(f"{W}tbl")

    tbl_pr = etree.SubElement(tbl, f"{W}tblPr")
    etree.SubElement(tbl_pr, f"{W}tblStyle", {f"{W}val": "ad"})
    etree.SubElement(tbl_pr, f"{W}tblW", {f"{W}w": "0", f"{W}type": "auto"})
    etree.SubElement(
        tbl_pr,
        f"{W}tblLook",
        {
            f"{W}val": "04A0",
            f"{W}firstRow": "1",
            f"{W}lastRow": "0",
            f"{W}firstColumn": "1",
            f"{W}lastColumn": "0",
            f"{W}noHBand": "0",
            f"{W}noVBand": "1",
        },
    )

    grid = etree.SubElement(tbl, f"{W}tblGrid")
    for width in COL_WIDTHS:
        etree.SubElement(grid, f"{W}gridCol", {f"{W}w": str(width)})

    header_cells = [(title, width) for title, width in zip(TABLE_HEADERS, COL_WIDTHS, strict=True)]
    tbl.append(make_table_row(header_cells, header=True))

    for entry in entries:
        data_cells = [
            (str(entry.number), COL_WIDTHS[0]),
            (entry.path, COL_WIDTHS[1]),
            (entry.size, COL_WIDTHS[2]),
        ]
        tbl.append(make_table_row(data_cells))

    return tbl


def find_appendix_file_paragraphs(body: etree._Element) -> list[etree._Element]:
    children = list(body)
    appendix_started = False
    file_paragraphs: list[etree._Element] = []

    for child in children:
        if child.tag != f"{W}p":
            if appendix_started and file_paragraphs:
                break
            continue

        text = paragraph_text(child)
        style = paragraph_style(child)

        if style == "H0Center" and text == APPENDIX_TITLE:
            appendix_started = True
            file_paragraphs.clear()
            continue

        if not appendix_started:
            continue

        match = FILE_RE.match(text)
        if match:
            file_paragraphs.append(child)
            continue

        if file_paragraphs:
            break

    if not appendix_started:
        raise RuntimeError(f"Could not find H0Center '{APPENDIX_TITLE}' in document.xml")
    if not file_paragraphs:
        raise RuntimeError("Could not find file list paragraphs in Appendix A")

    return file_paragraphs


def parse_file_entries(file_paragraphs: list[etree._Element]) -> list[FileEntry]:
    entries: list[FileEntry] = []
    for index, paragraph in enumerate(file_paragraphs, start=1):
        text = paragraph_text(paragraph)
        match = FILE_RE.match(text)
        if not match:
            raise RuntimeError(f"Unexpected paragraph format at entry {index}: {text!r}")
        entries.append(FileEntry(number=index, path=match.group(1), size=match.group(2)))
    return entries


def apply_table(body: etree._Element, entries: list[FileEntry], file_paragraphs: list[etree._Element]) -> int:
    table = build_file_table(entries)
    insert_before = file_paragraphs[0]
    insert_index = body.index(insert_before)
    body.insert(insert_index, table)

    removed = 0
    for paragraph in file_paragraphs:
        body.remove(paragraph)
        removed += 1
    return removed


def build_appendix_table(docx_path: Path, backup_path: Path, *, dry_run: bool) -> None:
    document = load_document(docx_path)
    body = document.find(f"{W}body")
    if body is None:
        raise RuntimeError("word/document.xml: <w:body> not found")

    file_paragraphs = find_appendix_file_paragraphs(body)
    entries = parse_file_entries(file_paragraphs)
    insert_index = list(body).index(file_paragraphs[0])

    print(f"docx: {docx_path}")
    print(f"files_found: {len(entries)}")
    print(f"insert_index: {insert_index}")
    first = entries[0]
    last = entries[-1]
    print(f"first_row: {first.number} | {first.path} | {first.size}".encode("ascii", "backslashreplace").decode())
    print(f"last_row: {last.number} | {last.path} | {last.size}".encode("ascii", "backslashreplace").decode())

    if dry_run:
        print("dry-run: no changes written")
        return

    shutil.copy2(docx_path, backup_path)
    removed = apply_table(body, entries, file_paragraphs)
    write_document_xml(docx_path, document)
    print(f"backup: {backup_path}")
    print(f"removed_paragraphs: {removed}")
    print(f"table_rows: {len(entries) + 1}")
    print(f"updated: {docx_path}")


def main() -> None:
    root = repo_root()
    parser = argparse.ArgumentParser(description="Build Appendix A file table in VKR DOCX")
    parser.add_argument(
        "--docx",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.1.1.docx",
    )
    parser.add_argument(
        "--backup",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.1.1.backup.docx",
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.docx.is_file():
        raise SystemExit(f"DOCX file not found: {args.docx}")

    build_appendix_table(args.docx, args.backup, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
