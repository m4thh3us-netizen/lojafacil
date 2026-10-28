package com.lojafacil.shared.security;

public final class Access {
    public static final String OWNER = "hasRole('OWNER')";

    public static final String MANAGEMENT = "hasAnyRole('OWNER','MANAGER')";

    public static final String OPERATIONS = "hasAnyRole('OWNER','MANAGER','OPERATOR')";

    private Access() {
    }
}
