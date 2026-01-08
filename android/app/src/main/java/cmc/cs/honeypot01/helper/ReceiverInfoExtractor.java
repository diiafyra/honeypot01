package cmc.cs.honeypot01.helper;

import android.content.Context;

/**
 * Interface để lấy thông tin số điện thoại/SIM nhận cuộc gọi
 */
public interface ReceiverInfoExtractor {

    /**
     * Lấy số điện thoại của thiết bị (nếu có)
     * @return Số điện thoại hoặc null
     */
    String getReceiverNumber();

    /**
     * Lấy subscription ID (SIM slot)
     * @return Subscription ID hoặc null
     */
    Integer getSubscriptionId();

    /**
     * Factory method
     */
    static ReceiverInfoExtractor create(Context context) {
        return new ReceiverInfoExtractorImpl(context);
    }
}