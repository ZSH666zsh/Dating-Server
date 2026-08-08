package com.dating.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dating.payment.constant.ErrorCode;
import com.dating.payment.entity.CoinAccount;
import com.dating.payment.entity.CoinLedger;
import com.dating.payment.exception.BizException;
import com.dating.payment.mapper.CoinAccountMapper;
import com.dating.payment.mapper.CoinLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 金币服务。
 * 对应 payment-service-design.md §6。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinService {

    private final CoinAccountMapper accountMapper;
    private final CoinLedgerMapper ledgerMapper;

    /**
     * 获取金币余额。
     */
    public CoinAccount getCoins(Long userId) {
        CoinAccount account = accountMapper.selectById(userId);
        if (account == null) {
            account = new CoinAccount();
            account.setUserId(userId);
            account.setBalance(0L);
            account.setPaidBalance(0L);
            account.setVersion(0);
        }
        return account;
    }

    /**
     * 消费金币（先扣免费，免费不够扣付费）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CoinAccount consumeCoins(Long userId, long amount, String reason, String extra, String idempotencyKey) {
        // 幂等检查
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            CoinLedger existing = ledgerMapper.selectOne(
                    new LambdaQueryWrapper<CoinLedger>()
                            .eq(CoinLedger::getUserId, userId)
                            .eq(CoinLedger::getIdempotencyKey, idempotencyKey));
            if (existing != null) {
                // 已扣过，返回上次结果。如果不存在 → 继续
                CoinAccount account = accountMapper.selectById(userId);
                if (account == null) throw new BizException(ErrorCode.INTERNAL_ERROR, "账户异常");
                return account;
            }
        }

        // 读取账户
        CoinAccount account = accountMapper.selectById(userId);
        if (account == null) {
            throw new BizException(ErrorCode.INSUFFICIENT_COINS, "金币不足");
        }

        // 分层扣减
        long freeTake = Math.min(account.getBalance(), amount);  // 免费币够，全扣免费
        long paidTake = amount - freeTake;

        if (paidTake > account.getPaidBalance()) {
            throw new BizException(ErrorCode.INSUFFICIENT_COINS, "金币不足");
        }

        // 乐观锁更新
        account.setBalance(account.getBalance() - freeTake);
        account.setPaidBalance(account.getPaidBalance() - paidTake);
        int rows = accountMapper.updateById(account);
        if (rows == 0) {  // 影响行数=0 → version 不匹配（并发冲突）→ 抛异常让调用方重试
            throw new BizException(ErrorCode.INTERNAL_ERROR, "并发冲突，请重试");
        }

        // 写流水
        CoinLedger ledger = new CoinLedger();
        ledger.setUserId(userId);
        ledger.setType("EXPENSE");
        ledger.setAmount(freeTake);
        ledger.setPaidAmount(paidTake);
        ledger.setBalanceAfter(account.getBalance());
        ledger.setPaidBalanceAfter(account.getPaidBalance());
        ledger.setReason(reason);
        ledger.setExtra(extra);
        ledger.setIdempotencyKey(idempotencyKey);

        try {
            ledgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            // 如果插入时，幂等键冲突：已有流水，查历史返回，catch 后忽略（另一线程已插入）
            log.warn("Idempotency key duplicate: userId={} key={}", userId, idempotencyKey);
        }

        return account;
    }

    /**
     * 增加免费金币。
     */
    @Transactional(rollbackFor = Exception.class)
    public CoinAccount addCoins(Long userId, long amount, String reason) {
        CoinAccount account = getCoins(userId);
        account.setBalance(account.getBalance() + amount);
        if (account.getVersion() == null) {
            account.setVersion(0);
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }

        CoinLedger ledger = new CoinLedger();
        ledger.setUserId(userId);
        ledger.setType("INCOME");
        ledger.setAmount(amount);
        ledger.setBalanceAfter(account.getBalance());
        ledger.setPaidBalanceAfter(account.getPaidBalance());
        ledger.setReason(reason);
        ledgerMapper.insert(ledger);
        return account;
    }

    /**
     * 增加付费金币。
     */
    @Transactional(rollbackFor = Exception.class)
    public CoinAccount addPaidCoins(Long userId, long amount, String reason) {
        CoinAccount account = getCoins(userId);
        account.setPaidBalance(account.getPaidBalance() + amount);
        if (account.getVersion() == null) {
            account.setVersion(0);
            accountMapper.insert(account);
        } else {
            accountMapper.updateById(account);
        }

        CoinLedger ledger = new CoinLedger();
        ledger.setUserId(userId);
        ledger.setType("INCOME");
        ledger.setPaidAmount(amount);
        ledger.setBalanceAfter(account.getBalance());
        ledger.setPaidBalanceAfter(account.getPaidBalance());
        ledger.setReason(reason);
        ledgerMapper.insert(ledger);
        return account;
    }

    /**
     * 获取金币流水。
     */
    public CoinLedgerListResult getCoinLedger(Long userId, int page, int size) {
        long total = ledgerMapper.selectCount(
                new LambdaQueryWrapper<CoinLedger>().eq(CoinLedger::getUserId, userId));
        var entries = ledgerMapper.selectList(
                new LambdaQueryWrapper<CoinLedger>()
                        .eq(CoinLedger::getUserId, userId)
                        .orderByDesc(CoinLedger::getCreatedAt)
                        .last("OFFSET " + (page - 1) * size + " LIMIT " + size));
        return new CoinLedgerListResult(entries, (int) total);
    }

    public record CoinLedgerListResult(java.util.List<CoinLedger> entries, int total) {}
}
