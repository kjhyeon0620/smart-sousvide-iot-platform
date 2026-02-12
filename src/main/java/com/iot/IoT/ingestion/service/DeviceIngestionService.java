package com.iot.IoT.ingestion.service;

import com.iot.IoT.ingestion.dto.DeviceStatusMessage;

public interface DeviceIngestionService {

    void ingest(DeviceStatusMessage message);
}
