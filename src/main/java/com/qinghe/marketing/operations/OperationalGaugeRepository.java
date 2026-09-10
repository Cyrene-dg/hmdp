package com.qinghe.marketing.operations;

import java.util.Map;

public interface OperationalGaugeRepository {
    Map<String, Long> snapshot();
}
