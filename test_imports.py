#!/usr/bin/env python
import sys
print("Python:", sys.version)
try:
    import numpy
    print("✓ NumPy:", numpy.__version__)
except Exception as e:
    print("✗ NumPy error:", e)

try:
    import pandas
    print("✓ Pandas:", pandas.__version__)
except Exception as e:
    print("✗ Pandas error:", e)

try:
    from flask import Flask
    print("✓ Flask:", Flask.__version__)
except Exception as e:
    print("✗ Flask error:", e)

try:
    from sqlalchemy import __version__
    print("✓ SQLAlchemy:", __version__)
except Exception as e:
    print("✗ SQLAlchemy error:", e)

print("\nTesting app import...")
try:
    from app import app
    print("✓ App imported successfully")
except Exception as e:
    print("✗ App import error:", e)
    import traceback
    traceback.print_exc()
