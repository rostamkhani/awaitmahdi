import psycopg2
from psycopg2 import OperationalError
import os
import sys

def create_connection(db_name, db_user, db_password, db_host, db_port):
    connection = None
    try:
        connection = psycopg2.connect(
            database=db_name,
            user=db_user,
            password=db_password,
            host=db_host,
            port=db_port,
        )
        print(f"Connection to PostgreSQL DB successful with user '{db_user}'")
    except OperationalError as e:
        # Don't print full stack for auth failure during brute force
        if "password authentication failed" in str(e):
             return None
        print(f"The error '{e}' occurred")
    return connection

def get_databases():
    # Attempt 1: Environment Variables
    db_user = os.getenv("DB_USER", "postgres")
    db_password = os.getenv("DB_PASSWORD", None)
    db_host = os.getenv("DB_HOST", "localhost")
    db_port = os.getenv("DB_PORT", "5432")
    
    connection = None
    
    # Common passwords to try if no env var or default fails
    common_passwords = [
        "postgres", 
        "password", 
        "123456", 
        "taajil", 
        "admin", 
        "root", 
        "ShomaraP^ss4Click"
    ]
    
    if db_password:
        print(f"Trying password from environment variable...")
        connection = create_connection("postgres", db_user, db_password, db_host, db_port)
    
    if not connection:
        print("Trying common passwords...")
        for pwd in common_passwords:
            print(f"Trying password: '{pwd}'")
            connection = create_connection("postgres", db_user, pwd, db_host, db_port)
            if connection:
                print(f"SUCCESS! The password is: '{pwd}'")
                break
    
    if not connection:
        print("\nCould not connect to database.")
        print("Please check your credentials or set DB_PASSWORD environment variable.")
        sys.exit(1)

    if connection:
        cursor = connection.cursor()
        try:
            cursor.execute("SELECT datname FROM pg_database WHERE datistemplate = false;")
            databases = cursor.fetchall()
            
            print("\nList of Databases:")
            print("-" * 20)
            for db in databases:
                print(db[0])
                
        except Exception as e:
            print(f"Error executing query: {e}")
        finally:
            cursor.close()
            connection.close()
            print("\nConnection closed.")

if __name__ == "__main__":
    get_databases()
