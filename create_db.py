import psycopg2
from psycopg2.extensions import ISOLATION_LEVEL_AUTOCOMMIT
import os

def create_database():
    # Credentials from previous success
    db_user = "postgres"
    db_password = "password" # Trying default or '123456' based on previous context. 
                             # Wait, the user's terminal showed '123456' was the success password.
    db_password = "123456"
    db_host = "localhost"
    db_port = "5432"

    try:
        # Connect to default 'postgres' db
        conn = psycopg2.connect(
            user=db_user,
            password=db_password,
            host=db_host,
            port=db_port,
            database="postgres"
        )
        conn.set_isolation_level(ISOLATION_LEVEL_AUTOCOMMIT)
        cursor = conn.cursor()
        
        # Check if exists
        cursor.execute("SELECT 1 FROM pg_catalog.pg_database WHERE datname = 'taajil'")
        exists = cursor.fetchone()
        
        if not exists:
            cursor.execute("CREATE DATABASE taajil")
            print("Database 'taajil' created successfully.")
        else:
            print("Database 'taajil' already exists.")
            
        cursor.close()
        conn.close()
        
    except Exception as e:
        print(f"Error creating database: {e}")

if __name__ == "__main__":
    create_database()

