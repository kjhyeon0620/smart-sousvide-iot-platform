package com.iot.IoT.anomaly;

import java.util.Set;

/**
 * @param state  next state, or null when the device turned OFF and every window resets
 * @param active anomaly types whose condition holds after this message
 */
public record AnomalyEvaluation(DeviceAnomalyState state, Set<AnomalyType> active) {
}
