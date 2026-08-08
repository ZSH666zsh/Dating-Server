package com.dating.payment.constant;

public class ErrorCode {
    public static final int OK = 0;
    public static final int INTERNAL_ERROR = 5000;
    public static final int INSUFFICIENT_COINS = 3001;
    public static final int ORDER_NOT_FOUND = 2001;
    public static final int PAYMENT_FAILED = 2002;
    public static final int INVALID_PARAM = 4001;
    public static final int NOT_IMPLEMENTED = 9999;

    private ErrorCode() {}
}
