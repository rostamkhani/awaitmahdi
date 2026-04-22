from fastapi import FastAPI, Depends, HTTPException, status, Request
from fastapi.security import OAuth2PasswordBearer
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from jose import JWTError, jwt
from . import crud, models, schemas, auth, database
from .database import engine

models.Base.metadata.create_all(bind=engine)

app = FastAPI()

# CORS configuration - Allow all origins for mobile access
origins = [
    "http://localhost:5173",
    "http://localhost:5174",
    "http://127.0.0.1:5173",
    "http://127.0.0.1:5174",
    "http://192.168.99.140:5173",
    "http://192.168.99.140:5174",
    "*"  # Allow all for development
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="login")

def get_db():
    db = database.SessionLocal()
    try:
        yield db
    finally:
        db.close()

async def get_current_user_optional(token: str = Depends(oauth2_scheme)):
    # This is a bit tricky because OAuth2PasswordBearer raises 401 if missing.
    # We'll make a custom dependency or just handle it in the endpoint.
    return None

def get_current_user_from_token(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, auth.SECRET_KEY, algorithms=[auth.ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
    user = crud.get_user_by_username(db, username=username)
    if user is None:
        raise credentials_exception
    return user

@app.post("/register", response_model=schemas.Token)
def register(user: schemas.UserCreate, db: Session = Depends(get_db)):
    db_user = crud.get_user_by_username(db, username=user.username)
    if db_user:
        raise HTTPException(status_code=400, detail="Username already registered")
    
    # Create user
    new_user = crud.create_user(db=db, user=user)
    
    # Auto login
    access_token = auth.create_access_token(
        data={"sub": new_user.username, "user_id": new_user.id}
    )
    return {
        "access_token": access_token, 
        "token_type": "bearer", 
        "user_id": new_user.id,
        "username": new_user.username
    }

@app.post("/login", response_model=schemas.Token)
def login(user: schemas.UserLogin, db: Session = Depends(get_db)):
    db_user = crud.get_user_by_username(db, username=user.username)
    if not db_user or not auth.verify_password(user.password, db_user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    access_token = auth.create_access_token(
        data={"sub": db_user.username, "user_id": db_user.id}
    )
    return {
        "access_token": access_token, 
        "token_type": "bearer",
        "user_id": db_user.id,
        "username": db_user.username
    }

@app.post("/heartbeat", response_model=schemas.Stats)
def heartbeat(
    data: schemas.SalavatSync, 
    request: Request,
    db: Session = Depends(get_db)
):
    """
    Unified endpoint: Sync salavat count (can be 0) and return updated stats.
    This ensures atomicity: we save first, then return the stats that include the saved count.
    """
    # Try to get user from Authorization header if present
    user_id = None
    auth_header = request.headers.get('Authorization')
    if auth_header and auth_header.startswith("Bearer "):
        token = auth_header.split(" ")[1]
        try:
            payload = jwt.decode(token, auth.SECRET_KEY, algorithms=[auth.ALGORITHM])
            user_id = payload.get("user_id")
        except:
            pass # Invalid token, treat as guest if uuid provided
            
    guest_uuid = data.guest_uuid
    
    # Save count (even if 0, this is a heartbeat to get latest stats)
    if data.count > 0:
        if user_id:
            crud.add_salavat_log(db, data.count, user_id=user_id)
        elif guest_uuid:
            crud.add_salavat_log(db, data.count, guest_uuid=guest_uuid)
    
    # Return updated stats (which includes the count we just saved)
    return crud.get_stats(db, user_id=user_id, guest_uuid=guest_uuid)

# Keep /stats for backward compatibility, but recommend using /heartbeat
@app.get("/stats", response_model=schemas.Stats)
def get_stats(
    guest_uuid: str = None, 
    request: Request = None,
    db: Session = Depends(get_db)
):
    user_id = None
    if request:
        auth_header = request.headers.get('Authorization')
        if auth_header and auth_header.startswith("Bearer "):
            token = auth_header.split(" ")[1]
            try:
                payload = jwt.decode(token, auth.SECRET_KEY, algorithms=[auth.ALGORITHM])
                user_id = payload.get("user_id")
            except:
                pass
            
    return crud.get_stats(db, user_id=user_id, guest_uuid=guest_uuid)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
