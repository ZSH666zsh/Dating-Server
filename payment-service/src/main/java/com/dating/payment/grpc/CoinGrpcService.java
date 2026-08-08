package com.dating.payment.grpc;

import com.dating.payment.constant.ErrorCode;
import com.dating.payment.exception.BizException;
import com.dating.payment.service.CoinService;
import com.dating.zhaoshihang.proto.payment.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * 金币 gRPC 服务端实现。
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class CoinGrpcService extends CoinServiceGrpc.CoinServiceImplBase {

    private final CoinService coinService;

    @Override
    public void getCoins(GetCoinsRequest req, StreamObserver<GetCoinsResponse> resp) {
        try {
            var account = coinService.getCoins(req.getUserId());
            resp.onNext(GetCoinsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setBalance(account.getBalance() != null ? account.getBalance() : 0)
                    .setPaidBalance(account.getPaidBalance() != null ? account.getPaidBalance() : 0)
                    .setTotalBalance((account.getBalance() != null ? account.getBalance() : 0)
                            + (account.getPaidBalance() != null ? account.getPaidBalance() : 0))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getCoins error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }

    @Override
    public void consumeCoins(ConsumeCoinsRequest req, StreamObserver<ConsumeCoinsResponse> resp) {
        try {
            var account = coinService.consumeCoins(req.getUserId(), req.getAmount(),
                    req.getReason(), req.getExtra(), req.getIdempotencyKey());
            resp.onNext(ConsumeCoinsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setBalanceAfter(account.getBalance() + account.getPaidBalance())
                    .build());
            resp.onCompleted();
        } catch (BizException e) {
            resp.onNext(ConsumeCoinsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(e.getCode()).setMessage(e.getMessage()))
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("consumeCoins error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }

    @Override
    public void addCoins(AddCoinsRequest req, StreamObserver<AddCoinsResponse> resp) {
        try {
            var account = coinService.addCoins(req.getUserId(), req.getAmount(), req.getReason());
            resp.onNext(AddCoinsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setBalanceAfter(account.getBalance())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("addCoins error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }

    @Override
    public void addPaidCoins(AddPaidCoinsRequest req, StreamObserver<AddPaidCoinsResponse> resp) {
        try {
            var account = coinService.addPaidCoins(req.getUserId(), req.getAmount(), req.getReason());
            resp.onNext(AddPaidCoinsResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setPaidBalanceAfter(account.getPaidBalance())
                    .build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("addPaidCoins error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }

    @Override
    public void getCoinLedger(GetCoinLedgerRequest req, StreamObserver<GetCoinLedgerResponse> resp) {
        try {
            var result = coinService.getCoinLedger(req.getUserId(), req.getPage(), req.getSize());
            var builder = GetCoinLedgerResponse.newBuilder()
                    .setBase(BaseResponse.newBuilder().setCode(ErrorCode.OK))
                    .setTotal(result.total());
            for (var entry : result.entries()) {
                builder.addEntries(CoinLedgerEntry.newBuilder()
                        .setId(entry.getId())
                        .setType(entry.getType())
                        .setAmount(entry.getAmount())
                        .setBalanceAfter(entry.getBalanceAfter())
                        .setReason(entry.getReason() != null ? entry.getReason() : "")
                        .build());
            }
            resp.onNext(builder.build());
            resp.onCompleted();
        } catch (Exception e) {
            log.error("getCoinLedger error", e);
            resp.onError(Status.INTERNAL.withDescription("内部错误").asRuntimeException());
        }
    }
}
