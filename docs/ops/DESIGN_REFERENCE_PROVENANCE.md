# Design Reference Provenance

**R-32 compliance.** The review asked for provenance, licensing and attribution
to be recorded for the third-party UI reference material used during visual
design. The material itself is deliberately **not** in the repository — this
document is what stays, so the record survives even though the files do not.

## What the material is

`design-refs/` (gitignored, ~176 MB across 14 files) holds community-published
UI kits that recreate the interfaces of two widely-used messaging apps. They
were consulted as *visual reference* during the Phase 5 design pass.

| Directory | Contents | Approx. size |
|---|---|---|
| `design-refs/archives/` | The three original community ZIPs, kept unmodified as the provenance record | 78 MB |
| `design-refs/telegram/` | Extracted PNG/SVG/PDF exports of the Telegram UI kit | 77 MB |
| `design-refs/whatsapp/` | Extracted PNG/SVG exports of the WhatsApp UI kit | 21 MB |

The `telegram/` and `whatsapp/` directories are **extractions of the ZIPs**, not
independent sources. Retaining both is duplication; if the material is ever
re-gathered, keep the archives and extract on demand — one canonical format per
reference, as the review recommends.

## Provenance

Both kits are community-contributed Figma files, obtained from the Figma
Community, and were downloaded to a local working directory. They are **fan
recreations published by third-party designers**, not official assets released
by Meta/WhatsApp or by Telegram.

> **Fill in before any redistribution:** the exact Figma Community URL, the
> publishing designer, and the licence stated on each file's community page.
> These were not captured at download time. Do not redistribute any of this
> material until that is recorded and the licence is confirmed to permit it.

## Licensing position

The safe assumption, and the one this project operates under:

1. **Assume no redistribution right.** Community UI kits are commonly published
   under permissive-looking terms, but the *trade dress they depict* — WhatsApp's
   and Telegram's interfaces, names and logos — is not the publisher's to
   license. A permissive licence on the Figma file does not grant rights over
   the depicted brands.
2. **Reference, don't copy.** The material informed layout intuition and spacing
   rhythm. No asset, icon, colour token or string from either kit ships in this
   app; the implemented design lives in `:design-system` and is our own.
3. **Never ship it, never commit it.** `design-refs/` is gitignored precisely so
   that neither a clone nor a source archive redistributes third-party brand
   material.

## Attribution

No attribution appears in the app, because no third-party asset appears in the
app. If any element ever *is* derived directly from one of these kits, that
derivation must be recorded here and attributed in-app before release.

## Related trade-dress note

The green outgoing-bubble preset in `ChatStyle.bubblePresets` is described in
code as "the classic messenger green … refs-adjacent hues". It is an
independently chosen colour offered as one preset among several and never the
default. That is a deliberate line: offering a familiar-feeling theme is not the
same as shipping another product's assets.

## If you need to share design context

Give a new contributor the `:design-system` module and a build of the app. Do
not send the reference zips — see
[`DISTRIBUTION_CHECKLIST.md`](DISTRIBUTION_CHECKLIST.md).
