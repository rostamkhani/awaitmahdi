from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class UserCreate(BaseModel):
    username: str
    password: str

class UserLogin(BaseModel):
    username: str
    password: str

class Token(BaseModel):
    access_token: str
    token_type: str
    user_id: str
    username: str

class SalavatSync(BaseModel):
    count: int
    guest_uuid: Optional[str] = None
    # timestamp could be added if client tracks it, but server time is safer for sorting
    
class Stats(BaseModel):
    today_total: int
    all_time_total: int
    user_today: int
    user_total: int

