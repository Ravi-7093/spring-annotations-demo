package com.demo.exception;

/** A CHECKED exception. Used to show @Transactional does NOT roll back on checked exceptions by default. */
public class ReportFailedException extends Exception {
    public ReportFailedException(String msg) { super(msg); }
}
