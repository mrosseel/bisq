package io.bisq.api.model.payment;

import bisq.core.payment.payload.PaymentAccountPayload;

public interface PaymentAccountConverter<B extends bisq.core.payment.PaymentAccount, BP extends PaymentAccountPayload, R extends io.bisq.api.model.payment.PaymentAccount> {

    B toBusinessModel(R rest);

    R toRestModel(B business);

    R toRestModel(BP business);

}
