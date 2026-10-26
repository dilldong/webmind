package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/3/7
 */
@Slf4j
public class TransactionUtils {

    /**
     * 事务提交成功后执行，非事务环境下直接执行
     * 1.挂载"事务钩子"，在已有事务的生命周期节点插入回调
     * 2.在已有事务提交成功后才执行，顺序有保障
     */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            // 非事务环境直接执行（如单元测试、脚本任务）
            action.run();
        }
    }

    /**
     * 主动开启 & 提交事务
     */
    public static boolean startAndCommit(TransactionTemplate template, Supplier<Boolean> action) {
        Boolean result = template.execute(txStatus -> {
            try {
                if (Boolean.TRUE.equals(action.get()))
                    return Boolean.TRUE;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }

            txStatus.setRollbackOnly();
            return Boolean.FALSE;
        });

        return Boolean.TRUE.equals(result);
    }
}
