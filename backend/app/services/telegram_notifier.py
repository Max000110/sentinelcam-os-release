import logging
import httpx
from typing import Optional
from app.core.config import settings

logger = logging.getLogger("sentinelcam.telegram")

class TelegramNotifier:
    def __init__(self):
        self.bot_token = settings.TELEGRAM_BOT_TOKEN
        self.chat_id = settings.TELEGRAM_CHAT_ID

    async def send_motion_alert(
        self,
        device_name: str,
        device_id: str,
        timestamp_str: str,
        snapshot_bytes: Optional[bytes] = None
    ) -> bool:
        if not self.bot_token or not self.chat_id:
            logger.info("Telegram notification skipped (bot_token or chat_id not configured)")
            return False

        message = (
            f"🚨 *SentinelCam Motion Alert*\n\n"
            f"📹 *Camera:* {device_name} (`{device_id}`)\n"
            f"⏰ *Time:* {timestamp_str}\n"
            f"⚠️ Motion detected on local CCTV node."
        )

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                if snapshot_bytes:
                    url = f"https://api.telegram.org/bot{self.bot_token}/sendPhoto"
                    files = {"photo": ("snapshot.jpg", snapshot_bytes, "image/jpeg")}
                    data = {"chat_id": self.chat_id, "caption": message, "parse_mode": "Markdown"}
                    res = await client.post(url, data=data, files=files)
                else:
                    url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"
                    data = {"chat_id": self.chat_id, "text": message, "parse_mode": "Markdown"}
                    res = await client.post(url, json=data)

                if res.status_code == 200:
                    logger.info(f"Telegram alert sent for device {device_id}")
                    return True
                else:
                    logger.error(f"Failed to send Telegram alert: {res.status_code} - {res.text}")
                    return False
        except Exception as e:
            logger.error(f"Telegram dispatch error: {e}")
            return False

telegram_notifier = TelegramNotifier()
