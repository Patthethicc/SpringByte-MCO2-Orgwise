package com.fintech.orgwise.treasury.security;

/**
 * Role names carried in the JWT {@code role} claim (identity-service's {@code Role} enum).
 * Spring authorities are these names with the {@code ROLE_} prefix.
 */
public final class Roles {

    /** System administrator. */
    public static final String ADMIN = "ADMIN";
    /** Organisation president: final approver, may view the treasury. */
    public static final String PRESIDENT = "PRESIDENT";
    /** Finance head: the only role allowed to disburse. */
    public static final String FINANCE_HEAD = "FINANCE_HEAD";
    /** Operations head: approver. */
    public static final String OPERATIONS_HEAD = "OPERATIONS_HEAD";
    /** Regular member who files expenses. */
    public static final String ASSOCIATE = "ASSOCIATE";
    /** Role of tokens minted by a service for service-to-service calls with no user behind them. */
    public static final String SERVICE = "SERVICE";

    private Roles() {
    }
}
