# File: streaming_poc/dockers/fastapi-app/Dockerfile

# 1. Base Image
FROM python:3.10-slim

# 2. Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# 3. Set the working directory in the container
WORKDIR /app

# 4. Copy the FastAPI application code
#    These paths are relative to the ROOT CONTEXT (`streaming_poc/`)
COPY fastapi-app/ /app/

# 5. Install dependencies using pip directly from pyproject.toml
#    This uses the modern PEP 517/518 build system
RUN pip install --no-cache-dir -e .

# 6. Expose the port the app runs on
EXPOSE 8000

# 7. Define the command to run the application
# Environment-aware startup (production by default)
CMD ["python", "run.py"]