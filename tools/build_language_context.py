"""Snapshot guide excerpts and operator examples into inspectable Lain bindings."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sections(path, selected):
    text = path.read_text()
    blocks = re.split(r"(?=^## \d+\.)", text, flags=re.M)
    picked = [block for block in blocks if re.match(r"## (\d+)\.", block)
              and int(re.match(r"## (\d+)\.", block)[1]) in selected]
    return (f"Source: {path.relative_to(ROOT)}; SHA256 {hashlib.sha256(text.encode()).hexdigest()}.\n"
            + "\n".join(picked))


def build():
    core = sections(ROOT / "docs/how-to-think-in-lain.md", {1, 3, 4, 8, 9, 11, 13, 16, 17})
    examples = sections(ROOT / "docs/how-to-think-in-lain-examples.md", {1, 2, 4, 7, 8, 11, 12, 13})
    catalog = (ROOT / "docs/operator-examples.edn").read_text()
    matches = list(re.finditer(r':operators (\[[^\]]*\]) :source ("(?:\\.|[^"\\])*") :checks ', catalog))
    rendered = []
    for index, match in enumerate(matches):
        operators = ", ".join(re.findall(r'"([^"\n]+)"', match[1]))
        source = json.loads(match[2])
        end = catalog.find("\n\n{", match.end())
        observations = catalog[match.end():end if end >= 0 else len(catalog)].rstrip()
        observations = observations.removesuffix("]").rstrip() if end < 0 else observations
        observations = observations.removesuffix("}")
        rendered.append(f"Operators: {operators}\nLain source:\n{source}\nExpected observations (test metadata):\n{observations}\n")
    assert len(rendered) == catalog.count(":operators "), "Operator catalog parser lost an example"
    note = ("These are language documentation excerpts, not source to admit verbatim. "
            "The early guide's schematic helpers are not additional capabilities. "
            "For temporal reduction use the qualified behavior:* APIs documented in operator-examples-cell; "
            "the guide's bounded visualization history is a separate operator. "
            "The combinator library is already loaded. Return executable Lain only, without Markdown fences.\n\n")
    declarations = {
        "guide-core-cell": note + core,
        "guide-principles-cell": note + sections(ROOT / "docs/how-to-think-in-lain.md", {1, 4}),
        "dynamic-expressions-cell": (
            "Every expression builds connected propagators. A function call whose operands are cells "
            "is reactive: it recomputes when their strongest values change. event:point is NOT limited "
            "to literal IDs and times. data:get extracts fields reactively. No callback, listener or "
            "new source compilation is needed for each input. Never bypass event:point to make inputs dynamic.\n"
            + next(item for item in rendered if "doc:changing-reading" in item)
            + "Verification publishes reading-1/time=1/value=7, then replaces the same input cell with "
            "reading-2/time=2/value=11. Without recompilation the projected vector changes from [7] to [7 11]. "
            "These publications are test metadata; this is documentation, not an installed running example.\n"),
        "orientation-example-cell": (
            "This is code to read, not a running example. Numeric occurrences become a behavior, "
            "a scan emits numeric prefixes, and latest-value projects an ordinary number. "
            "Memory is a plain record of explicit cell references. Write through cell:input.\n"
            "(define demo:source (behavior:source))\n"
            "(-> (event:point \"a\" 1 2) demo:source)\n"
            "(-> (event:point \"b\" 2 3) demo:source)\n"
            "(define demo:sum (behavior:scan demo:source + 0))\n"
            "(define demo:reading (behavior:latest-value demo:sum 0))\n"
            "(-> (cell:value (cell:reference demo:reading)) (cell:input (data:get :orientation-value memory-cell)))\n"
            "(-> (inspect:cell (cell:reference demo:reading)) (cell:input (data:get :orientation-edge memory-cell)))\n"
            "Expected: memory orientation-value port = 5; memory orientation-edge port has edge/reference, value=5, inputs, outputs.\n"),
        "construction-examples-cell": "\n".join(item for item in rendered if any(
            key in item.split("\n", 1)[0] for key in (
                "behavior:", "event:point", "seq:", "data:", "model:request", "message-to-user", "memory-cell", "text:format"))),
        "guide-examples-cell": note + examples,
        "operator-examples-cell": (
            "Concrete executable examples and expected results for the advertised operators. "
            "Each example is independent and uses doc:* names. Core libraries and experiment capabilities "
            "are already installed. Tests compile these exact sources; no example implements a conversation "
            "history network. Effects are tested with controlled transport and the real session delivery "
            "extension. Read expected observations as test metadata, not additional Lain forms.\n\n" + "\n".join(rendered)),
    }
    target = ROOT / "lib/language-context.lain"
    target.write_text(";; Generated by tools/build_language_context.py from the recorded guide/catalog sources.\n"
                      + "\n".join(f"(define {name}\n  {json.dumps(body, ensure_ascii=False)})\n"
                                  for name, body in declarations.items()))


if __name__ == "__main__":
    build()
