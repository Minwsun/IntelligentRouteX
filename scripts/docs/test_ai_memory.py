from __future__ import annotations

import unittest

from ai_memory_lib import combine_sections_into_chunks, parse_sections, slugify


class AiMemoryLibTest(unittest.TestCase):
    def test_slugify_should_normalize_titles(self) -> None:
        self.assertEqual(slugify("Heavy-rain rescue & stress lane"), "heavy-rain-rescue-stress-lane")

    def test_parse_sections_should_capture_heading_path(self) -> None:
        body = "# Title\n\n## Child\n\nContent here.\n"
        sections = parse_sections(body, "Fallback")
        self.assertEqual(sections[0]["heading_path"], ["Fallback", "Title"])
        self.assertIn("Content here.", sections[-1]["text"])

    def test_combine_sections_should_keep_heading_boundaries(self) -> None:
        sections = [
            {"heading_path": ["Doc", "One"], "text": "Word " * 100},
            {"heading_path": ["Doc", "Two"], "text": "Word " * 120},
            {"heading_path": ["Doc", "Three"], "text": "Word " * 110},
        ]
        chunks = combine_sections_into_chunks(sections, target_min_words=150, target_max_words=260)
        self.assertGreaterEqual(len(chunks), 2)
        self.assertEqual(chunks[0]["heading_path"], ["Doc", "One"])


if __name__ == "__main__":
    unittest.main()
