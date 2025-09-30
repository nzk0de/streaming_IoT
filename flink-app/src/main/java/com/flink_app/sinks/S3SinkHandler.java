package com.flink_app.sinks;

import com.flink_app.config.AppConfig;
import com.flink_app.model.SensorSample;
import com.flink_app.model.TransformedRecord;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S3SinkHandler {

    private static final Logger LOG = LoggerFactory.getLogger(S3SinkHandler.class);

    private final StreamTableEnvironment tableEnv;
    private final AppConfig appConfig;

    public S3SinkHandler(StreamTableEnvironment tableEnv, AppConfig appConfig) {
        this.tableEnv = tableEnv;
        this.appConfig = appConfig;
    }

    public void sinkToS3Flattened(DataStream<TransformedRecord> inferredStream) {

        // 1. Define flat TypeInformation for the output Row
        TypeInformation<?>[] fieldTypes = new TypeInformation<?>[]{
            BasicTypeInfo.STRING_TYPE_INFO,  // sensor_id (used only for partitioning)
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // timestamp
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // acc_x
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // acc_y
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // acc_z
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // gyro_x
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // gyro_y
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // gyro_z
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // mag_x
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // mag_y
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // mag_z
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // accl_mag
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // gyro_mag
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // accl_sum_of_changes
            BasicTypeInfo.DOUBLE_TYPE_INFO,  // gyro_mag_energy
            BasicTypeInfo.DOUBLE_TYPE_INFO   // predicted_velocity
        };

        String[] fieldNames = new String[]{
            "sensor_id", // will be used as partition column, dropped in downstream use
            "cur_timestamp", "acc_x", "acc_y", "acc_z",
            "gyro_x", "gyro_y", "gyro_z",
            "mag_x", "mag_y", "mag_z",
            "accl_mag", "gyro_mag",
            "accl_sum_of_changes", "gyro_mag_energy", "predicted_velocity"
        };

        RowTypeInfo rowTypeInfo = new RowTypeInfo(fieldTypes, fieldNames);

        // 2. Flatten TransformedRecord to DataStream<Row>
        DataStream<Row> flatRowStream = inferredStream
            .flatMap(new FlatMapFunction<TransformedRecord, Row>() {
                @Override
                public void flatMap(TransformedRecord record, Collector<Row> out) {
                    if (record.getSamples() != null) {
                        for (SensorSample sample : record.getSamples()) {
                            Row row = new Row(fieldNames.length);
                            row.setField(0, record.getSensor_id());
                            row.setField(1, sample.getTimestamp());
                            row.setField(2, sample.getAcc_x());
                            row.setField(3, sample.getAcc_y());
                            row.setField(4, sample.getAcc_z());
                            row.setField(5, sample.getGyro_x());
                            row.setField(6, sample.getGyro_y());
                            row.setField(7, sample.getGyro_z());
                            row.setField(8, sample.getMag_x());
                            row.setField(9, sample.getMag_y());
                            row.setField(10, sample.getMag_z());
                            row.setField(11, sample.getAccl_mag());
                            row.setField(12, sample.getGyro_mag());
                            row.setField(13, sample.getPredicted_velocity());
                            out.collect(row);
                        }
                    }
                }
            })
            .returns(rowTypeInfo)
            .name("FlatMapToFlatRow");

        // 3. Define schema for Table (sensor_id and dt are only for partitioning)
        Schema schema = Schema.newBuilder()
            .column("sensor_id", DataTypes.STRING()) // Partitioned, not stored in downstream
            .column("cur_timestamp", DataTypes.DOUBLE())
            .column("acc_x", DataTypes.DOUBLE())
            .column("acc_y", DataTypes.DOUBLE())
            .column("acc_z", DataTypes.DOUBLE())
            .column("gyro_x", DataTypes.DOUBLE())
            .column("gyro_y", DataTypes.DOUBLE())
            .column("gyro_z", DataTypes.DOUBLE())
            .column("mag_x", DataTypes.DOUBLE())
            .column("mag_y", DataTypes.DOUBLE())
            .column("mag_z", DataTypes.DOUBLE())
            .column("accl_mag", DataTypes.DOUBLE())
            .column("gyro_mag", DataTypes.DOUBLE())
            .column("predicted_velocity", DataTypes.DOUBLE())
            .columnByExpression("dt", "DATE_FORMAT(PROCTIME(), 'yyyy-MM-dd')")
            .build();

        // 4. Convert to Table
        Table flatTable = tableEnv.fromDataStream(flatRowStream, schema);
        tableEnv.createTemporaryView("flat_sensor_data_view", flatTable);

        // 5. Define S3 Sink Table DDL
        String s3OutputPath = "s3a://" + appConfig.s3outputPath;
        String createSinkSql = String.format(
            "CREATE TABLE flat_s3_sink (" +
            "  sensor_id STRING," +
            "  cur_timestamp DOUBLE," +
            "  acc_x DOUBLE," +
            "  acc_y DOUBLE," +
            "  acc_z DOUBLE," +
            "  gyro_x DOUBLE," +
            "  gyro_y DOUBLE," +
            "  gyro_z DOUBLE," +
            "  mag_x DOUBLE," +
            "  mag_y DOUBLE," +
            "  mag_z DOUBLE," +
            "  accl_mag DOUBLE," +
            "  gyro_mag DOUBLE," +
            "  predicted_velocity DOUBLE," +
            "  dt STRING" +
            ") PARTITIONED BY (dt, sensor_id) " +
            "WITH (" +
            "  'connector' = 'filesystem'," +
            "  'path' = '%s'," +
            "  'format' = 'parquet'," +
            "  'parquet.compression' = 'SNAPPY'," +
            "  'sink.partition-commit.trigger' = 'process-time'," +
            "  'sink.partition-commit.delay' = '1 min'," +
            "  'sink.partition-commit.policy.kind' = 'success-file'," +
            "  'sink.rolling-policy.file-size' = '128MB'," +
            " 'sink.rolling-policy.check-interval' = '1 min'," +
            "  'sink.rolling-policy.rollover-interval' = '1 min'" +
            ")",
            s3OutputPath
        );

        tableEnv.executeSql(createSinkSql);

        // 6. Insert into sink table
        String insertSql =
            "INSERT INTO flat_s3_sink " +
            "SELECT " +
            "  sensor_id, cur_timestamp, acc_x, acc_y, acc_z," +
            "  gyro_x, gyro_y, gyro_z, mag_x, mag_y, mag_z," +
            "  accl_mag, gyro_mag_energy, predicted_velocity, dt " +
            "FROM flat_sensor_data_view";

        tableEnv.executeSql(insertSql);
        LOG.info("Streaming sink to S3 (flattened) configured successfully.");
    }
}
