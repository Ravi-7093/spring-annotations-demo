package com.demo.service;

import com.demo.config.TxLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    /*
     * @Transactional(propagation = REQUIRES_NEW)
     *   "Pause the caller's transaction and start a brand-new one just for me."
     *   This one commits/rolls back ON ITS OWN. So the audit row is kept
     *   even if the caller's transaction later rolls back.
     *
     * Console when called from UserService.create():
     *   [TX] UserService.create | active=true | name=com.demo.service.UserService.create
     *   [TX] AuditService.log   | active=true | name=com.demo.service.AuditService.log   <- different tx name
     *   [TX] AuditService.log -> COMMITTED                                                 <- commits first
     *   [TX] UserService.create -> COMMITTED
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, Object data) {
        System.out.println("[AUDIT] " + action + " -> " + data);
        TxLogger.log("AuditService.log");
    }

    /*
     * @Transactional(propagation = MANDATORY)
     *   "A transaction MUST already exist, otherwise throw IllegalTransactionStateException."
     *   Use it for methods that should never run on their own.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void mustBeInsideTx() {
        TxLogger.log("AuditService.mustBeInsideTx");
    }
}
