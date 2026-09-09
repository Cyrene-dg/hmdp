package com.qinghe.marketing.campaign;

public final class InventoryAdjustmentResult {

    private final InventoryAdjustment adjustment;
    private final long beforeTotalStock;
    private final long afterTotalStock;

    public InventoryAdjustmentResult(InventoryAdjustment adjustment,
                                     long beforeTotalStock, long afterTotalStock) {
        this.adjustment = adjustment;
        this.beforeTotalStock = beforeTotalStock;
        this.afterTotalStock = afterTotalStock;
    }

    public InventoryAdjustment adjustment() { return adjustment; }
    public long beforeTotalStock() { return beforeTotalStock; }
    public long afterTotalStock() { return afterTotalStock; }
}
