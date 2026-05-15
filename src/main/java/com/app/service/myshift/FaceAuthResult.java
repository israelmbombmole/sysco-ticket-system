package com.app.service.myshift;

public final class FaceAuthResult {

    private final boolean ok;
    private final double score01;
    private final String method;
    private final String detail;

    public FaceAuthResult(boolean ok, double score01, String method, String detail) {
        this.ok = ok;
        this.score01 = score01;
        this.method = method;
        this.detail = detail;
    }

    public boolean isOk() {
        return ok;
    }

    /** Score between 0 and 1, when known. */
    public double getScore01() {
        return score01;
    }

    public String getMethod() {
        return method;
    }

    public String getDetail() {
        return detail;
    }
}
