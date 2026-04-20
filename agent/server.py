"""
WebSocket server for real-time Hank agent conversation.
Accepts text queries with optional image frames from connected clients (e.g., Android glasses app).
Streams responses back in real-time.

Run: python -m agent.server
"""

import asyncio
import json
import logging
from typing import Any

import websockets
from websockets.server import WebSocketServerProtocol

from agent.session import ConversationSession

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


async def handle_client(websocket: WebSocketServerProtocol) -> None:
    """Handle a connected WebSocket client with its own ConversationSession."""
    client_addr = websocket.remote_address
    logger.info(f"Client connected: {client_addr}")

    session = ConversationSession()
    try:
        async for message in websocket:
            try:
                data: dict[str, Any] = json.loads(message)
                text = data.get("text", "").strip()
                frame = data.get("frame")  # base64-encoded JPEG or None
                media_type = data.get("media_type", "image/jpeg")

                if not text:
                    await websocket.send(
                        json.dumps({"type": "error", "text": "No text provided"})
                    )
                    continue

                logger.info(f"Query from {client_addr}: {text[:50]}..." + ("" if not frame else " (with image)"))

                # Stream response chunks
                async for chunk in session.query(text, frame, media_type):
                    await websocket.send(
                        json.dumps({"type": "chunk", "text": chunk})
                    )

                # Signal end of response
                await websocket.send(json.dumps({"type": "done"}))

            except json.JSONDecodeError:
                await websocket.send(
                    json.dumps({"type": "error", "text": "Invalid JSON"})
                )
            except Exception as e:
                logger.exception(f"Error processing query from {client_addr}: {e}")
                await websocket.send(
                    json.dumps({"type": "error", "text": str(e)})
                )

    except websockets.exceptions.ConnectionClosed:
        logger.info(f"Client disconnected: {client_addr}")
    except Exception as e:
        logger.exception(f"Unexpected error for client {client_addr}: {e}")


async def main(host: str = "0.0.0.0", port: int = 8765) -> None:
    """Start the WebSocket server."""
    logger.info(f"Starting Hank agent server on ws://{host}:{port}")

    async with websockets.serve(handle_client, host, port):
        logger.info(f"✓ Hank agent server running on ws://{host}:{port}")
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    asyncio.run(main())
