package com.wtechitsolutions.domain;

public enum Library {
    BEANIO,
    FIXFORMAT4J,
    FIXEDLENGTH,
    BINDY,
    CAMEL_BEANIO,
    VELOCITY,
    SPRING_BATCH,
    /** Spring Batch flat-file components, column layout derived from fixedformat4j annotations. */
    SPRING_BATCH_FIXFORMAT4J,
    /** Spring Batch flat-file components, column layout derived from fixedlength annotations. */
    SPRING_BATCH_FIXEDLENGTH
}
