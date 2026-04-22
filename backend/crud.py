from sqlalchemy.orm import Session
from sqlalchemy import func
from . import models, schemas, auth
from datetime import datetime, date, timedelta

def get_user_by_username(db: Session, username: str):
    return db.query(models.User).filter(models.User.username == username).first()

def create_user(db: Session, user: schemas.UserCreate):
    hashed_password = auth.get_password_hash(user.password)
    db_user = models.User(username=user.username, hashed_password=hashed_password)
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user

def add_salavat_log(db: Session, count: int, user_id: str = None, guest_uuid: str = None):
    if count <= 0:
        return None
    
    # Check for an existing record within the last 15 minutes for this user
    fifteen_mins_ago = datetime.utcnow() - timedelta(minutes=15)
    
    query = db.query(models.Salavat).filter(models.Salavat.created_at >= fifteen_mins_ago)
    
    if user_id:
        query = query.filter(models.Salavat.user_id == user_id)
    elif guest_uuid:
        query = query.filter(models.Salavat.guest_uuid == guest_uuid)
    else:
        # Should not happen
        return None
        
    # Get the latest one to update
    existing_record = query.order_by(models.Salavat.created_at.desc()).first()
    
    if existing_record:
        # Update existing record
        existing_record.count += count
        # Optionally update the timestamp to extend the window? 
        # User requirement: "if he comes back sooner than 15 mins... stored in same record"
        # "but if 2 hours later... new record".
        # If we update created_at, the window slides. If we don't, it's a fixed 15 min window from start.
        # Usually "within 15 minutes" implies a sliding window or fixed blocks.
        # Let's keep the window fixed based on the first creation time of that record
        # to strictly satisfy "record for every 15 minutes".
        # actually, if I update created_at, I extend the session. 
        # Let's assume strict 15 min buckets from the *start* of the record.
        db.commit()
        db.refresh(existing_record)
        return existing_record
    else:
        # Create new record
        db_salavat = models.Salavat(
            user_id=user_id,
            guest_uuid=guest_uuid,
            count=count,
            created_at=datetime.utcnow()
        )
        db.add(db_salavat)
        db.commit()
        db.refresh(db_salavat)
        return db_salavat

def get_stats(db: Session, user_id: str = None, guest_uuid: str = None):
    # Global Stats
    all_time_total = db.query(func.sum(models.Salavat.count)).scalar() or 0
    
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    today_total = db.query(func.sum(models.Salavat.count)).filter(
        models.Salavat.created_at >= today_start
    ).scalar() or 0
    
    # User Stats
    user_total = 0
    user_today = 0
    
    if user_id:
        user_total = db.query(func.sum(models.Salavat.count)).filter(
            models.Salavat.user_id == user_id
        ).scalar() or 0
        user_today = db.query(func.sum(models.Salavat.count)).filter(
            models.Salavat.user_id == user_id,
            models.Salavat.created_at >= today_start
        ).scalar() or 0
    elif guest_uuid:
        user_total = db.query(func.sum(models.Salavat.count)).filter(
            models.Salavat.guest_uuid == guest_uuid
        ).scalar() or 0
        user_today = db.query(func.sum(models.Salavat.count)).filter(
            models.Salavat.guest_uuid == guest_uuid,
            models.Salavat.created_at >= today_start
        ).scalar() or 0
        
    return {
        "today_total": today_total,
        "all_time_total": all_time_total,
        "user_today": user_today,
        "user_total": user_total
    }
