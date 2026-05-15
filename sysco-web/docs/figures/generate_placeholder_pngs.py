"""Write simple labeled placeholder PNGs without third-party deps (stdlib only)."""
from __future__ import annotations

import struct
import zlib
from pathlib import Path


def _png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    return struct.pack("!I", len(data)) + chunk_type + data + struct.pack("!I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF)


def write_solid_png(path: Path, width: int, height: int, rgb: tuple[int, int, int]) -> None:
    raw = bytearray()
    r, g, b = rgb
    row = bytes([0, r, g, b] * width)
    for _ in range(height):
        raw.extend(row)
    compressed = zlib.compress(bytes(raw), level=6)
    ihdr = struct.pack("!IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += _png_chunk(b"IHDR", ihdr)
    png += _png_chunk(b"IDAT", compressed)
    png += _png_chunk(b"IEND", b"")
    path.write_bytes(png)


# 5x7 font for caps, digits, space, hyphen (bitmap columns left-to-right)
_FONT: dict[str, list[int]] = {}
for ch, bits in [
    ("0", [0x3E, 0x51, 0x49, 0x45, 0x3E]),
    ("1", [0x00, 0x42, 0x7F, 0x40, 0x00]),
    ("2", [0x42, 0x61, 0x51, 0x49, 0x46]),
    ("3", [0x21, 0x41, 0x45, 0x4B, 0x31]),
    ("4", [0x18, 0x14, 0x12, 0x7F, 0x10]),
    ("5", [0x27, 0x45, 0x45, 0x45, 0x39]),
    ("6", [0x3C, 0x4A, 0x49, 0x49, 0x30]),
    ("7", [0x01, 0x71, 0x09, 0x05, 0x03]),
    ("8", [0x36, 0x49, 0x49, 0x49, 0x36]),
    ("9", [0x06, 0x49, 0x49, 0x29, 0x1E]),
    ("A", [0x7E, 0x11, 0x11, 0x11, 0x7E]),
    ("B", [0x7F, 0x49, 0x49, 0x49, 0x36]),
    ("C", [0x3E, 0x41, 0x41, 0x41, 0x22]),
    ("D", [0x7F, 0x41, 0x41, 0x22, 0x1C]),
    ("E", [0x7F, 0x49, 0x49, 0x49, 0x41]),
    ("F", [0x7F, 0x09, 0x09, 0x09, 0x01]),
    ("G", [0x3E, 0x41, 0x49, 0x49, 0x7A]),
    ("H", [0x7F, 0x08, 0x08, 0x08, 0x7F]),
    ("I", [0x00, 0x41, 0x7F, 0x41, 0x00]),
    ("L", [0x7F, 0x40, 0x40, 0x40, 0x40]),
    ("M", [0x7F, 0x02, 0x04, 0x02, 0x7F]),
    ("N", [0x7F, 0x04, 0x08, 0x10, 0x7F]),
    ("O", [0x3E, 0x41, 0x41, 0x41, 0x3E]),
    ("P", [0x7F, 0x09, 0x09, 0x09, 0x06]),
    ("R", [0x7F, 0x09, 0x19, 0x29, 0x46]),
    ("S", [0x46, 0x49, 0x49, 0x49, 0x31]),
    ("T", [0x01, 0x01, 0x7F, 0x01, 0x01]),
    ("U", [0x3F, 0x40, 0x40, 0x40, 0x3F]),
    ("W", [0x1F, 0x20, 0x40, 0x20, 0x1F]),
    ("Y", [0x07, 0x08, 0x70, 0x08, 0x07]),
    (" ", [0x00, 0x00, 0x00, 0x00, 0x00]),
    ("-", [0x08, 0x08, 0x08, 0x08, 0x08]),
    (".", [0x00, 0x60, 0x60, 0x00, 0x00]),
    ("_", [0x40, 0x40, 0x40, 0x40, 0x40]),
]:
    _FONT[ch] = bits


def _draw_char(
    pixels: list[list[tuple[int, int, int]]],
    x0: int,
    y0: int,
    ch: str,
    fg: tuple[int, int, int],
) -> None:
    bits = _FONT.get(ch.upper(), _FONT[" "])
    for col in range(5):
        colbits = bits[col]
        for row in range(7):
            if colbits & (1 << row):
                yy, xx = y0 + row, x0 + col
                if 0 <= yy < len(pixels) and 0 <= xx < len(pixels[0]):
                    pixels[yy][xx] = fg


def _draw_text(
    pixels: list[list[tuple[int, int, int]]],
    x: int,
    y: int,
    text: str,
    fg: tuple[int, int, int],
) -> None:
    for ch in text:
        _draw_char(pixels, x, y, ch, fg)
        x += 6


def write_labeled_png(path: Path, line1: str, line2: str, accent: tuple[int, int, int]) -> None:
    w, h = 880, 495
    bg = (245, 248, 252)
    bar = accent
    pixels: list[list[tuple[int, int, int]]] = [[bg for _ in range(w)] for _ in range(h)]
    for yy in range(72):
        for xx in range(w):
            pixels[yy][xx] = bar
    # subtext area
    sub = (80, 90, 110)
    _draw_text(pixels, 24, 96, line1[:48], sub)
    _draw_text(pixels, 24, 120, line2[:48], (40, 45, 55))
    hint = "REPLACE WITH SCREENSHOT + RED ARROWS"
    _draw_text(pixels, 24, h - 36, hint[:48], (180, 90, 90))

    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b in row:
            raw.extend((r, g, b))
    compressed = zlib.compress(bytes(raw), level=6)
    ihdr = struct.pack("!IIBBBBB", w, h, 8, 2, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += _png_chunk(b"IHDR", ihdr)
    png += _png_chunk(b"IDAT", compressed)
    png += _png_chunk(b"IEND", b"")
    path.write_bytes(png)


def main() -> None:
    here = Path(__file__).resolve().parent
    specs: list[tuple[str, str, str, tuple[int, int, int]]] = [
        ("fig-01-application-shell.png", "FIG 01", "APPLICATION SHELL", (41, 98, 155)),
        ("fig-02-general-navigation-flow.png", "FIG 02", "NAVIGATION FLOW", (52, 120, 95)),
        ("fig-03-ticket-lifecycle.png", "FIG 03", "TICKET LIFECYCLE", (142, 68, 173)),
        ("fig-04-courier-flow.png", "FIG 04", "COURIER FLOW", (189, 120, 45)),
        ("fig-05-job-scheduler-flow.png", "FIG 05", "JOB SCHEDULER", (60, 130, 180)),
        ("fig-06-login-page.png", "FIG 06", "LOGIN PAGE", (200, 80, 80)),
        ("fig-07-password-change.png", "FIG 07", "PASSWORD CHANGE", (200, 80, 80)),
        ("fig-08-guided-tour-step.png", "FIG 08", "GUIDED TOUR", (120, 100, 180)),
        ("fig-09-ticket-monitoring.png", "FIG 09", "TICKET MONITORING", (142, 68, 173)),
        ("fig-10-ticket-detail.png", "FIG 10", "TICKET DETAIL", (142, 68, 173)),
        ("fig-11-create-ticket.png", "FIG 11", "CREATE TICKET", (142, 68, 173)),
        ("fig-12-my-work-inbox.png", "FIG 12", "MON TRAVAIL", (70, 130, 180)),
        ("fig-13-courier-portal.png", "FIG 13", "PORTAIL COURRIER", (189, 120, 45)),
        ("fig-14-data-entry-grid.png", "FIG 14", "SAISIE DONNEES", (45, 130, 120)),
        ("fig-15-data-share-form.png", "FIG 15", "PARTAGE DONNEES", (45, 130, 120)),
        ("fig-16-notifications-inbox.png", "FIG 16", "NOTIFICATIONS", (100, 100, 100)),
        ("fig-17-chat-thread.png", "FIG 17", "CHAT", (100, 100, 100)),
        ("fig-18-user-management.png", "FIG 18", "USER MANAGEMENT", (160, 60, 60)),
        ("fig-19-login-audit-export.png", "FIG 19", "LOGIN AUDIT", (160, 60, 60)),
    ]
    for fname, l1, l2, rgb in specs:
        write_labeled_png(here / fname, l1, l2, rgb)
        print("wrote", fname)


if __name__ == "__main__":
    main()
