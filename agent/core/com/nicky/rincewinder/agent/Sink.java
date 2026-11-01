package com.nicky.rincewinder.agent;

/** Cíl pro řádky logu. Na těle zapisuje do RINCEAGT.LOG, v testech do konzole. */
public interface Sink {
    void log(String line);
}
