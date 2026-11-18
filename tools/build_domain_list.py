#!/usr/bin/env python3
"""Builds the compact adult-site list bundled with the app.

Usage (run with -I, input is downloaded/untrusted data):
    curl -sSL -o /tmp/lists/porn-nl.txt \\
        https://raw.githubusercontent.com/blocklistproject/Lists/master/alt-version/porn-nl.txt
    python3 -I tools/build_domain_list.py /tmp/lists/porn-nl.txt app/src/main/assets/adult_domains.bin

Input: one domain per line ('#' comments allowed). Output format is read by
core/.../model/DomainHashSet.kt: magic "NBDS", int version 1, int count, then sorted signed
64-bit FNV-1a hashes, all big-endian. Subdomains of listed domains are dropped (the app checks
parent domains anyway) and a leading "www." is removed.
"""
import struct
import sys

MASK = (1 << 64) - 1


def fnv1a64(s: str) -> int:
    h = 0xCBF29CE484222325
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * 0x100000001B3) & MASK
    return h - (1 << 64) if h >= (1 << 63) else h  # as signed, to match Kotlin Long


def main(src: str, dst: str) -> None:
    domains = set()
    with open(src, encoding="utf-8", errors="replace") as f:
        for line in f:
            d = line.strip().lower().rstrip(".")
            if not d or d.startswith("#") or any(c.isspace() for c in d) or "." not in d:
                continue
            if d.startswith("www."):
                d = d[4:]
            domains.add(d)

    def has_listed_parent(d: str) -> bool:
        parts = d.split(".")
        return any(".".join(parts[i:]) in domains for i in range(1, len(parts) - 1))

    kept = [d for d in domains if not has_listed_parent(d)]
    hashes = sorted({fnv1a64(d) for d in kept})
    with open(dst, "wb") as out:
        out.write(struct.pack(">4sii", b"NBDS", 1, len(hashes)))
        out.write(struct.pack(f">{len(hashes)}q", *hashes))
    print(f"{len(domains)} domains, {len(kept)} after dropping subdomains, wrote {dst}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
