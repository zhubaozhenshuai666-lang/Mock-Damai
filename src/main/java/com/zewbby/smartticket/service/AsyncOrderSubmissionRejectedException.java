package com.zewbby.smartticket.service;

import com.zewbby.smartticket.common.BusinessException;

/** 异步抢票在 Redis 库存预扣确定未发生前被拒绝，可安全重试。 */
public class AsyncOrderSubmissionRejectedException extends BusinessException {

    public AsyncOrderSubmissionRejectedException(String message) {
        super(message == null || message.isBlank() ? "抢票请求被拒绝" : message);
    }
}
