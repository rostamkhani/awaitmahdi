from sqlalchemy import Boolean, Column, ForeignKey, Integer, String, DateTime
from sqlalchemy.orm import relationship
from .database import Base
from datetime import datetime
import uuid

class User(Base):
    __tablename__ = "users"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    username = Column(String, unique=True, index=True) # Phone or Email
    hashed_password = Column(String, nullable=True) # Nullable for guest users if we stored them here, but we might keep guests separate or just use UUID
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    salavats = relationship("Salavat", back_populates="owner")

class Salavat(Base):
    __tablename__ = "salavats"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String, ForeignKey("users.id")) # Can link to registered user
    guest_uuid = Column(String, index=True, nullable=True) # For guest users
    count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    owner = relationship("User", back_populates="salavats")

