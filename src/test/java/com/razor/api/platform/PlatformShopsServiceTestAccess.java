package com.razor.api.platform;

/** Acesso de teste aos auxiliares de pacote do console (relatório e senha provisória). */
public final class PlatformShopsServiceTestAccess {
    private PlatformShopsServiceTestAccess() {}

    public static String signature(String s) { return ErrorLogBuffer.signature(s); }
    public static String mask(String s) { return ErrorLogBuffer.mask(s); }
    public static String cost(String model, long tin, long tout, long calls) { return PlatformShopsService.cost(model, tin, tout, calls); }
    public static String tempPassword() { return PlatformShopsService.tempPassword(); }
}
