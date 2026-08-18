import os
import logging
from datetime import datetime, timedelta, timezone
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from app.models.recordings import Recording
from app.models.events import AiEvent, MotionEvent

logger = logging.getLogger("sentinelcam.cleanup")

class CleanupWorker:
    @staticmethod
    async def run_storage_retention_cleanup(
        db: AsyncSession,
        retention_days: int = 7,
        force_quota_cleanup: bool = False
    ) -> int:
        cutoff_date = datetime.now(timezone.utc) - timedelta(days=retention_days)
        
        # Query expired, non-locked recordings
        query = select(Recording).where(
            Recording.created_at < cutoff_date,
            Recording.is_locked == False,
            Recording.deleted_at == None
        )
        result = await db.execute(query)
        recordings_to_delete = result.scalars().all()

        deleted_count = 0
        for rec in recordings_to_delete:
            try:
                # Remove file from disk if present
                if rec.file_path and os.path.exists(rec.file_path):
                    os.remove(rec.file_path)
                
                rec.deleted_at = datetime.now(timezone.utc)
                rec.status = "PURGED_BY_RETENTION"
                deleted_count += 1
            except Exception as e:
                logger.error(f"Failed to delete recording file {rec.file_path}: {e}")

        await db.commit()
        logger.info(f"Storage retention worker purged {deleted_count} expired recordings.")
        return deleted_count

cleanup_worker = CleanupWorker()
