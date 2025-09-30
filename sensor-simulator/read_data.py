import dask.dataframe as dd
import pandas as pd
# --- Plotly Imports ---
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import dotenv
import os
dotenv.load_dotenv()
S3_BASE_PATH = os.getenv("S3_OUTPUT_PATH")
# --- Configuration ---

# Point to the specific hour's partition directory.
S3_PARTITION_PATH = f"{S3_BASE_PATH}/2025-07-07/"

# The sensor ID you want to select.
TARGET_SENSOR_ID = "0"

# Ensure your AWS credentials are set as environment variables
# export AWS_ACCESS_KEY_ID="YOUR_KEY"
# export AWS_SECRET_ACCESS_KEY="YOUR_SECRET"

# --- Dask Data Reading and Filtering ---

print(f"Reading Parquet data from: {S3_PARTITION_PATH}")
print(f"Filtering for sensor_id = '{TARGET_SENSOR_ID}'")

try:
    # Read the Parquet files into a Dask DataFrame (lazy).
    ddf = dd.read_parquet(S3_PARTITION_PATH, engine="pyarrow")

    # Filter for the target sensor (lazy).
    sensor_0_ddf = ddf[ddf["sensor_id"] == TARGET_SENSOR_ID]

    # Trigger computation to get a Pandas DataFrame.
    print("\n--- Executing Dask computation ---")
    sensor_0_df = sensor_0_ddf.compute()

    # --- Data Preparation for Plotting ---

    # Sort by timestamp for a clean time-series plot.
    sensor_0_df = sensor_0_df.sort_values(by="cur_timestamp").reset_index(drop=True)
    # Convert timestamp to a datetime format for better axis labels.
    sensor_0_df["datetime"] = pd.to_datetime(sensor_0_df["cur_timestamp"], unit="s")

    print("\nComputation complete!")
    print(
        f"Loaded and sorted {len(sensor_0_df)} records for sensor_id = '{TARGET_SENSOR_ID}'."
    )
    from matplotlib import pyplot as plt

    plt.plot(
        sensor_0_df["datetime"],
        sensor_0_df["acc_z"],
        label="Accelerometer Z",
    )
    plt.xlabel("Time")
    plt.ylabel("Acceleration (g)")
    plt.title(f"Sensor Data for sensor_id = {TARGET_SENSOR_ID}")
    plt.legend()
    plt.savefig(f"sensor_{TARGET_SENSOR_ID}_matplotlib_plot.png")

    # %%
    if sensor_0_df.empty:
        print("No data found for this sensor in the specified partition. Cannot plot.")
    else:
        # --- Plotting with Plotly ---

        print("Generating interactive plot with Plotly...")

        # Create a figure with two subplots, one above the other, that share the x-axis
        fig = make_subplots(
            rows=2,
            cols=1,
            shared_xaxes=True,
            vertical_spacing=0.1,
            subplot_titles=(
                "Predicted Velocity Over Time",
                "Raw Accelerometer Readings",
            ),
        )

        # --- Plot 1: Predicted Velocity ---
        fig.add_trace(
            go.Scatter(
                x=sensor_0_df["datetime"],
                y=sensor_0_df["predicted_velocity"],
                mode="lines",
                name="Predicted Velocity",
                line=dict(color="red", width=2),
            ),
            row=1,
            col=1,
        )

        # --- Plot 2: Raw Accelerometer Data ---
        fig.add_trace(
            go.Scatter(
                x=sensor_0_df["datetime"],
                y=sensor_0_df["acc_x"],
                mode="lines",
                name="Accel X",
            ),
            row=2,
            col=1,
        )
        fig.add_trace(
            go.Scatter(
                x=sensor_0_df["datetime"],
                y=sensor_0_df["acc_y"],
                mode="lines",
                name="Accel Y",
            ),
            row=2,
            col=1,
        )
        fig.add_trace(
            go.Scatter(
                x=sensor_0_df["datetime"],
                y=sensor_0_df["acc_z"],
                mode="lines",
                name="Accel Z",
            ),
            row=2,
            col=1,
        )

        # --- Update Layout and Axis Titles ---
        fig.update_layout(
            title_text=f"Sensor Data for sensor_id = {TARGET_SENSOR_ID}",
            height=800,
            template="plotly_white",
            legend_traceorder="reversed",
        )

        # Update y-axis titles
        fig.update_yaxes(title_text="Velocity (units/s)", row=1, col=1)
        fig.update_yaxes(title_text="Acceleration (g)", row=2, col=1)

        # Update shared x-axis title
        fig.update_xaxes(title_text="Time", row=2, col=1)

        # --- Save the Plot ---

        # Save to an interactive HTML file (recommended)
        html_filename = f"sensor_{TARGET_SENSOR_ID}_plotly_plot.html"
        fig.write_html(html_filename)
        print(f"\nInteractive plot saved successfully as '{html_filename}'")
        print(
            "Open this file in your web browser to pan, zoom, and inspect data points."
        )

        # Optionally, save to a static PNG file (requires 'kaleido' to be installed)
        png_filename = f"sensor_{TARGET_SENSOR_ID}_plotly_plot.png"
        fig.write_image(png_filename, scale=2)  # scale=2 increases resolution
        print(f"Static image saved successfully as '{png_filename}'")

except Exception as e:
    print(f"\nAn error occurred: {e}")
