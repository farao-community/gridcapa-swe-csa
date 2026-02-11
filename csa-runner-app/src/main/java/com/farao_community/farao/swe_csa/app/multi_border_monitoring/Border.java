package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

public enum Border {
    FR_ES,
    PT_ES;

    @Override
    public String toString() {
        return name().replace('_', '-');
    }
}
