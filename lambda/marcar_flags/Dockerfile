FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt requirements-local.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-local.txt
COPY handler.py poller_local.py ./
CMD ["python", "-u", "poller_local.py"]