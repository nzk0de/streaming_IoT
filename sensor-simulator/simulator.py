import argparse
import asyncio
import math
import os
import random
import struct
import time
from collections import defaultdict

import boto3
import crcmod
import numpy as np  # Import NumPy for vectorized operations
import pandas as pd
from aiomqtt import Client, MqttError

# --- Configuration ---
NUM_OF_SAMPLES = 64
PUBLISH_INTERVAL = NUM_OF_SAMPLES / 200.0
TOPIC_PREFIX = "stream"
import dotenv


def get_mqtt_broker(force_local=False):
    if force_local:
        return "localhost"
    host = os.getenv("MQTT_BROKER_HOST", "localhost")
    return host


dotenv.load_dotenv(dotenv_path="../.env")
MQTT_BROKER_PORT = os.getenv("MQTT_BROKER_PORT", "1883")
MQTT_PORT = int(MQTT_BROKER_PORT)
# print(f"Using MQTT broker port: {MQTT_PORT}")
JITTER_RATIO = 0
REAL_DATA_START_OFFSET = 1097792
PARQUET_FILE_PATH = "/home/evo4hp_server/efs_data/p229_file.parquet"

# --- CRC + Header ---
crc16_mod = crcmod.mkCrcFun(0x18005, rev=True, initCrc=0x0000, xorOut=0x0000)
HEADER_FORMAT = "<BBH"
HEADER_ID_COMMAND = 0x7C
DATA_TYPE_IMU_RAW_COMBO_V3 = 0x1E

# --- Scaling Factors ---
GO_ACC_FACTOR = 0.488 / 1000.0
GO_GYRO_FACTOR = 140.0 / 1000.0
GO_MAG_FACTOR = 1.0 / 1711.0
TEMP_SCALE_FACTOR = 1.0

ACCEL_SCALE_FACTOR = 1.0 / GO_ACC_FACTOR
GYRO_SCALE_FACTOR = 1.0 / GO_GYRO_FACTOR
MAG_SCALE_FACTOR = 1.0 / GO_MAG_FACTOR

sample_counts = defaultdict(int)
df_real_data = None


# --- Utility Functions ---
def float_to_int16(val):
    return int(max(min(round(val), 32767), -32768))


def jitter(base, ratio):
    return random.uniform(base - base * ratio, base + base * ratio)


def sin_i16(sample_idx, frequency, amplitude, phase=0.0):
    val = amplitude * math.sin(2.0 * math.pi * frequency * sample_idx + phase)
    return float_to_int16(val)


# --- OPTIMIZATION 1: Vectorized Synthetic Data Generation ---
# This function creates the core sample data for ONE packet using NumPy.
# It's called only ONCE per main loop iteration, not 300 times.
def create_vectorized_sample_payload(sample_idx):
    """
    Generates the core data payload for one packet using fast, vectorized NumPy operations.
    """
    # Create an array of indices for the 64 samples
    indices = np.arange(sample_idx, sample_idx + NUM_OF_SAMPLES)

    # Calculate all 64 samples for each axis at once
    ax = (np.sin(2.0 * np.pi * 0.05 * indices) * 16000).astype(np.int16)
    ay = (np.sin(2.0 * np.pi * 0.05 * indices + 2 * np.pi / 3) * 16000).astype(np.int16)
    az = (np.sin(2.0 * np.pi * 0.05 * indices + 4 * np.pi / 3) * 16000).astype(np.int16)

    gx = (np.sin(2.0 * np.pi * 0.1 * indices) * 14).astype(np.int16)
    gy = (np.sin(2.0 * np.pi * 0.1 * indices + 2 * np.pi / 3) * 14).astype(np.int16)
    gz = (np.sin(2.0 * np.pi * 0.1 * indices + 4 * np.pi / 3) * 14).astype(np.int16)

    mx = (np.sin(2.0 * np.pi * 0.02 * indices) * 8000).astype(np.int16)
    my = (np.sin(2.0 * np.pi * 0.02 * indices + 2 * np.pi / 3) * 8000).astype(np.int16)
    mz = (np.sin(2.0 * np.pi * 0.02 * indices + 4 * np.pi / 3) * 8000).astype(np.int16)

    temp = (np.sin(2.0 * np.pi * 0.005 * indices) * 2000).astype(np.int16)

    # Assemble the payload by iterating over the pre-calculated NumPy arrays
    payload = bytearray()
    for i in range(NUM_OF_SAMPLES):
        payload += struct.pack(">hhh", ax[i], ay[i], az[i])
        payload += struct.pack(">hhh", gx[i], gy[i], gz[i])
        payload += struct.pack("<hhh", mx[i], my[i], mz[i])
        payload += struct.pack(">h", temp[i])

    return payload


# --- OPTIMIZATION 2: Fast Packet Finalization ---
# This function takes a pre-made payload and just adds the final touches.
# It is extremely lightweight and fast.
def finalize_packet(base_payload, base_idx):
    """
    Takes a pre-generated data payload, adds the sensor-specific metadata,
    and returns the final binary packet.
    """
    # Prepend sensor-specific metadata to the shared payload
    timestamp_us = int(time.time() * 1e6)
    final_payload = bytearray()
    final_payload += struct.pack("I", base_idx)
    final_payload += struct.pack("Q", timestamp_us)
    final_payload += struct.pack("H", NUM_OF_SAMPLES)
    final_payload += base_payload

    # Add header and CRC
    header = struct.pack(
        HEADER_FORMAT, HEADER_ID_COMMAND, DATA_TYPE_IMU_RAW_COMBO_V3, len(final_payload)
    )
    crc = crc16_mod(header + final_payload)
    return header + final_payload + struct.pack("<H", crc)


def create_packet_from_df(df_rows, base_idx):
    if df_rows.empty:
        return None
    timestamp_us = int(time.time() * 1e6)
    payload = bytearray()
    payload += struct.pack("I", base_idx)
    payload += struct.pack("Q", timestamp_us)
    payload += struct.pack("H", len(df_rows))

    for _, row in df_rows.iterrows():
        ax = float_to_int16(row["Accl_X_local"] * ACCEL_SCALE_FACTOR)
        ay = float_to_int16(row["Accl_Y_local"] * ACCEL_SCALE_FACTOR)
        az = float_to_int16(row["Accl_Z_local"] * ACCEL_SCALE_FACTOR)
        gx = float_to_int16(row["Gyro_X_local"] * GYRO_SCALE_FACTOR)
        gy = float_to_int16(row["Gyro_Y_local"] * GYRO_SCALE_FACTOR)
        gz = float_to_int16(row["Gyro_Z_local"] * GYRO_SCALE_FACTOR)
        mx = float_to_int16(row["Mag_X"] * MAG_SCALE_FACTOR)
        my = float_to_int16(row["Mag_Y"] * MAG_SCALE_FACTOR)
        mz = float_to_int16(row["Mag_Z"] * MAG_SCALE_FACTOR)
        temp = float_to_int16(row["temp_raw"] * TEMP_SCALE_FACTOR)

        payload += struct.pack(">hhh", ax, ay, az)
        payload += struct.pack(">hhh", gx, gy, gz)
        payload += struct.pack("<hhh", mx, my, mz)
        payload += struct.pack(">h", temp)

    header = struct.pack(
        HEADER_FORMAT, HEADER_ID_COMMAND, DATA_TYPE_IMU_RAW_COMBO_V3, len(payload)
    )
    crc = crc16_mod(header + payload)
    return header + payload + struct.pack("<H", crc)


async def publisher_worker(name, client, queue: asyncio.Queue):
    try:
        while True:
            topic, packet = await queue.get()
            try:
                # QoS=0 για γρήγορη εκκένωση χωρίς ACK
                await client.publish(topic, packet, qos=0)
            finally:
                queue.task_done()
    except asyncio.CancelledError:
        # καθαρό κλείσιμο
        return


# --- Unified Loop ---
async def simulate_all_sensors(
    client, num_sensors, duration, use_real_data, target_samples, sensor_names
):
    # Anchor για ρυθμό αποστολής
    simulation_start_time = time.monotonic()
    loop_count = 0

    base_sample = [0] * num_sensors
    df_index = [0] * num_sensors

    # Τοπικά topics (ίδια λογική με πριν)
    if sensor_names:
        if len(sensor_names) > num_sensors:
            raise ValueError("More sensor names provided than number of sensors")
        topics = [f"{TOPIC_PREFIX}/{name}" for name in sensor_names] + [
            f"{TOPIC_PREFIX}/{i}" for i in range(len(sensor_names), num_sensors)
        ]
    else:
        topics = [f"{TOPIC_PREFIX}/{i}" for i in range(num_sensors)]

    # --- ΝΕΟ: Ουρά δημοσιεύσεων με backpressure και workers
    # Αν η ουρά γεμίσει, το await στο put() μπλοκάρει => δεν σωρεύονται "pending publishes"
    publish_queue = asyncio.Queue(maxsize=2000)
    workers = [
        asyncio.create_task(publisher_worker(f"pub-{i}", client, publish_queue))
        for i in range(4)
    ]  # 4 workers είναι συνήθως αρκετοί

    print("\n--- Starting Simulation with queue-backed publisher ---")

    try:
        while True:
            # Exit conditions
            if target_samples is not None:
                if loop_count >= target_samples:
                    break
            elif time.monotonic() - simulation_start_time > duration:
                break

            # Προαιρετικό: προδημιουργία payload για συνθετικά
            synthetic_payload_template = None
            if not use_real_data:
                master_sample_idx = loop_count * NUM_OF_SAMPLES
                synthetic_payload_template = create_vectorized_sample_payload(
                    master_sample_idx
                )

            # Δημιουργία πακέτων & push στην ουρά (ΟΧΙ gather, ΟΧΙ εκατοντάδες tasks)
            for sensor_id in range(num_sensors):
                packet = None
                if use_real_data:
                    start_idx, end_idx = (
                        df_index[sensor_id],
                        df_index[sensor_id] + NUM_OF_SAMPLES,
                    )
                    df_slice = df_real_data.iloc[
                        start_idx : min(end_idx, len(df_real_data))
                    ]
                    if df_slice.empty:
                        df_index[sensor_id] = 0
                        continue
                    packet = create_packet_from_df(df_slice, base_sample[sensor_id])
                    df_index[sensor_id] += len(df_slice)
                else:
                    packet = finalize_packet(
                        synthetic_payload_template, base_sample[sensor_id]
                    )

                if packet:
                    sample_counts[sensor_id] += 1
                    base_sample[sensor_id] += NUM_OF_SAMPLES

                    # Αν η ουρά είναι γεμάτη, αυτό θα μπλοκάρει μέχρι να αδειάσει -> backpressure
                    await publish_queue.put((topics[sensor_id], packet))

            loop_count += 1

            # Ακριβής ρυθμός βάσει προγραμματισμένου χρόνου έναρξης επόμενου batch
            next_loop_start_time = simulation_start_time + loop_count * PUBLISH_INTERVAL
            sleep_duration = next_loop_start_time - time.monotonic()
            if sleep_duration > 0:
                await asyncio.sleep(sleep_duration)

        # Περιμένουμε να φύγουν ΟΛΑ τα δημοσιευμένα μηνύματα
        await publish_queue.join()
    finally:
        # Κλείσιμο workers καθαρά
        for w in workers:
            w.cancel()
        await asyncio.gather(*workers, return_exceptions=True)


# --- Main Entry ---
async def main(
    duration,
    num_sensors,
    use_real_data,
    target_samples,
    cleanup=True,
    sensor_names=None,
    local_mqtt=False,
):

    global df_real_data
    start_time = time.time()
    mqtt_broker = get_mqtt_broker(local_mqtt)
    print(f"{mqtt_broker}:{MQTT_PORT}")
    if cleanup:
        # --- S3 Cleanup ---
        bucket_name = "gkoufasbucket"
        cur_date = time.strftime("%Y-%m-%d")
        prefix = f"streaming_poc/output/{cur_date}/"

        s3 = boto3.client("s3")
        paginator = s3.get_paginator("list_objects_v2")
        pages = paginator.paginate(Bucket=bucket_name, Prefix=prefix)

        objects_to_delete = []
        for page in pages:
            for obj in page.get("Contents", []):
                print("Deleting:", obj["Key"])
                objects_to_delete.append({"Key": obj["Key"]})
                if len(objects_to_delete) == 1000:
                    s3.delete_objects(
                        Bucket=bucket_name, Delete={"Objects": objects_to_delete}
                    )
                    objects_to_delete = []
        if objects_to_delete:
            s3.delete_objects(Bucket=bucket_name, Delete={"Objects": objects_to_delete})
    if not target_samples and not duration:
        print("Not data to send, please specify duration or target_samples")
        print("Exiting...")
        return

    if use_real_data:
        try:
            print(f"Loading Parquet file: {PARQUET_FILE_PATH}")
            df = pd.read_parquet(PARQUET_FILE_PATH)
            if REAL_DATA_START_OFFSET < len(df):
                df = df.iloc[REAL_DATA_START_OFFSET:].reset_index(drop=True).fillna(0.0)
                required_cols = [
                    "Accl_X_local",
                    "Accl_Y_local",
                    "Accl_Z_local",
                    "Gyro_X_local",
                    "Gyro_Y_local",
                    "Gyro_Z_local",
                    "Mag_X",
                    "Mag_Y",
                    "Mag_Z",
                    "temp_raw",
                ]
                for col in required_cols:
                    if col not in df.columns:
                        raise ValueError(f"Missing column: {col}")
                    df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0.0)

                df_real_data = df
                print(f"Loaded {len(df_real_data)} real samples.")
            else:
                print(f"Start offset {REAL_DATA_START_OFFSET} beyond data length.")
                return
        except Exception as e:
            print(f"Failed to load real data: {e}")
            return

    print(f"Simulating {num_sensors} sensors for {duration} seconds...")
    print(f"Using MQTT broker: {mqtt_broker}:{MQTT_PORT}")
    try:
        async with Client(mqtt_broker, MQTT_PORT) as client:
            await simulate_all_sensors(
                client,
                num_sensors,
                duration,
                use_real_data,
                target_samples,
                sensor_names,
            )
    except MqttError as e:
        print(f"MQTT connection error: {e}")
    except Exception as ex:
        print(f"Simulation error: {ex}")

    total = sum(sample_counts.values())
    if target_samples:
        expected = num_sensors * target_samples
    else:
        expected = int((duration / PUBLISH_INTERVAL) * num_sensors)
    print(f"\nExpected packets: {expected}")
    print(f"Actual packets:   {total} ({total / expected * 100:.2f}%)")
    print(f"Actual Duration: {time.time() - start_time:.2f} seconds")
    expected_duration = duration if not target_samples else target_samples * 0.32
    print(f"Expected Duration: {expected_duration} seconds")


# --- CLI ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-d", "--duration", type=int, default=0)
    parser.add_argument("-n", "--num_sensors", type=int, default=100)
    parser.add_argument("-r", "--use_real_data", action="store_true")
    parser.add_argument("-t", "--target_samples", type=int)
    parser.add_argument(
        "-c", "--cleanup", action="store_true", help="Cleanup S3 bucket before running"
    )
    parser.add_argument(
        "-l", "--local_mqtt", action="store_true", help="Use local MQTT broker"
    )
    parser.add_argument(
        "-s",
        "--sensor_names",
        type=str,
        help="Comma-separated list of sensor names (e.g., s1,s2,s3). These will be used for the first N sensors.",
    )

    args = parser.parse_args()
    if args.sensor_names and args.num_sensors > 0:
        sensor_names = args.sensor_names.split(",") if args.sensor_names else None
    else:
        sensor_names = None
    try:
        asyncio.run(
            main(
                args.duration,
                args.num_sensors,
                args.use_real_data,
                args.target_samples,
                args.cleanup,
                sensor_names,
                args.local_mqtt,
            )
        )
    except KeyboardInterrupt:
        print("Simulation interrupted.")
