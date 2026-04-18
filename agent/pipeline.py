"""
Entry point for the automotive diagnostic pipeline.

Usage:
    python -m agent.pipeline <image_path_or_url> [--model <model_id>]
"""

import argparse
import sys

from dotenv import load_dotenv

from agent.client import DEFAULT_MODEL, analyze_image
from agent.prompts import build_system_prompt


def run(image_source: str, model: str = DEFAULT_MODEL) -> str:
    system_prompt = build_system_prompt()
    return analyze_image(image_source, system_prompt, model=model)


def main() -> None:
    load_dotenv()

    parser = argparse.ArgumentParser(description="Automotive diagnostic image analysis")
    parser.add_argument("image", help="Local file path or public URL of the image")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="OpenRouter model ID")
    args = parser.parse_args()

    try:
        result = run(args.image, model=args.model)
        print(result)
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
