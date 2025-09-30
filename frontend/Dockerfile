# Use an official Python base image
FROM python:3.10-slim

# Set working directory
WORKDIR /app

# Copy the rest of the code
COPY . /app

# Expose the same port uvicorn uses
EXPOSE 8501

# Command to run the server
CMD ["python","simple_server.py"]
