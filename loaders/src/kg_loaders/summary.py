"""Load outcome aggregation and human-readable reporting."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from time import monotonic


@dataclass
class LoadSummary:
    created: int = 0
    already_present: int = 0
    skipped: int = 0
    skip_reasons: list[str] = field(default_factory=list)
    _started_at: float = field(default_factory=monotonic, repr=False)
    elapsed_seconds: float = 0.0

    def add_results(self, results: list[dict[str, object]]) -> None:
        for result in results:
            status = result.get("status")
            if status == "created":
                self.created += 1
            elif status == "already_present":
                self.already_present += 1
            elif status == "rejected":
                self.skip(str(result.get("error") or "rejected by API"))

    def skip(self, reason: str) -> None:
        self.skipped += 1
        self.skip_reasons.append(reason)

    def finish(self) -> None:
        self.elapsed_seconds = monotonic() - self._started_at

    def format(self) -> str:
        elapsed = self.elapsed_seconds or monotonic() - self._started_at
        return (
            f"Load Summary: created={self.created} already_present={self.already_present} "
            f"skipped={self.skipped} elapsed={elapsed:.1f}s"
        )

    def print(self) -> None:
        self.finish()
        print(self.format())
        if self.skip_reasons:
            for reason, count in Counter(self.skip_reasons).most_common():
                print(f"  skipped ({count}): {reason}")
