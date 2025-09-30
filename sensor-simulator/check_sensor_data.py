import dask.dataframe as dd
import pandas as pd
import s3fs
import argparse
from datetime import datetime
import dotenv
import os
dotenv.load_dotenv()
S3_BASE_PATH = os.getenv("S3_OUTPUT_PATH")

# --- Argument Parsing ---
parser = argparse.ArgumentParser(description="Check sensor data for a given date")
parser.add_argument(
    "--date",
    type=str,
    default=datetime.today().strftime("%Y-%m-%d"),
    help="Date in YYYY-MM-DD format (default: today)",
)
parser.add_argument(
    "--sensor_id", type=str, default="0", help="Sensor ID to filter (default: '0')"
)
args = parser.parse_args()

S3_PARTITION_PATH = f"{S3_BASE_PATH}/{args.date}/"
TARGET_SENSOR_ID = args.sensor_id

print(f"Reading Parquet data from: {S3_PARTITION_PATH}")
print(f"Filtering for sensor_id = '{TARGET_SENSOR_ID}'")

try:
    # Read Parquet files lazily
    ddf = dd.read_parquet(S3_PARTITION_PATH, engine="pyarrow")

    # Filter for sensor
    sensor_ddf = ddf[ddf["sensor_id"] == TARGET_SENSOR_ID]

    print("\n--- Executing Dask computation ---")
    sensor_df = sensor_ddf.compute()

    # Sort by timestamp
    sensor_df = sensor_df.sort_values(by="cur_timestamp").reset_index(drop=True)

    # Convert timestamp to datetime
    sensor_df["datetime"] = pd.to_datetime(sensor_df["cur_timestamp"], unit="s")

    print("Data columns:", sensor_df.columns.tolist())
    if len(sensor_df) == 0:
        print(f"No records found for sensor_id = {TARGET_SENSOR_ID} on {args.date}.")
    else:
        print(
            f"Loaded and sorted {len(sensor_df)} records for sensor_id = {TARGET_SENSOR_ID}."
        )
        print(sensor_df.head())

except Exception as e:
    print(f"\nAn error occurred: {e}")
